package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.HiddenPiecePool;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.Position;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.rules.GameEngine;
import edu.bupt.jieqi.rules.StandardGameEngine;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ExpectiminimaxAgent implements Agent {
    private static final int MAX_TRANSPOSITION_ENTRIES = 200_000;
    private static final int QUIESCENCE_DEPTH = 3;

    private final Evaluator evaluator;
    private final GameEngine engine;
    private final GreedyAgent fallback;
    // 只记录对局中已经公开见过的明子，不记录暗子的真实身份，避免 AI 偷看信息。
    private final Map<Color, EnumMap<PieceType, Integer>> revealedMemory = new EnumMap<>(Color.class);
    private final Map<Color, EnumMap<PieceType, Integer>> previousVisibleCounts = new EnumMap<>(Color.class);
    private String previousObservationKey;

    public ExpectiminimaxAgent(Evaluator evaluator) {
        this(evaluator, new StandardGameEngine());
    }

    public ExpectiminimaxAgent(Evaluator evaluator, GameEngine engine) {
        this.evaluator = Objects.requireNonNull(evaluator);
        this.engine = Objects.requireNonNull(engine);
        this.fallback = new GreedyAgent(evaluator, engine);
        for (Color color : Color.values()) {
            revealedMemory.put(color, new EnumMap<>(PieceType.class));
            previousVisibleCounts.put(color, new EnumMap<>(PieceType.class));
        }
    }

    @Override
    public synchronized Move chooseMove(PlayerView view, SearchBudget budget) {
        if (view.legalMoves().isEmpty()) {
            throw new IllegalStateException("No legal move is available");
        }

        rememberRevealedPieces(view);
        Instant deadline = Instant.now().plus(budget.timeLimit());
        // PlayerView 只有公开棋盘；这里用可见信息和历史记忆重建搜索用的概率局面。
        GameState state = SearchSupport.stateFrom(
                view,
                SearchSupport.inferHiddenPool(view.board(), Color.RED, revealedMemory.get(Color.RED)),
                SearchSupport.inferHiddenPool(view.board(), Color.BLACK, revealedMemory.get(Color.BLACK)));
        Color perspective = view.perspective();
        SearchContext context = new SearchContext(deadline);
        int maxDepth = adjustedDepth(view, budget);
        Move bestMove = null;
        double bestScore = -Double.MAX_VALUE;
        // 先过滤掉会直接送将帅的走法，再按战术价值排序，提升 Alpha-Beta 剪枝效率。
        List<Move> candidates = orderedMoves(
                state,
                SearchSupport.fastSafetyFilteredMoves(state, view.legalMoves(), perspective, engine),
                perspective);

        for (Move move : candidates) {
            if (timedOut(deadline)) {
                break;
            }
            double score = expectedMoveValue(
                    state,
                    move,
                    perspective,
                    maxDepth - 1,
                    -SearchSupport.WIN_SCORE,
                    SearchSupport.WIN_SCORE,
                    context);
            if (Double.isNaN(score)) {
                break;
            }
            if (bestMove == null || score > bestScore) {
                bestMove = move;
                bestScore = score;
            }
        }
        return bestMove != null ? bestMove : fallback.chooseMove(view, budget);
    }

    @Override
    public String name() {
        return "Expectiminimax";
    }

    private double value(
            GameState state,
            Color perspective,
            int depth,
            double alpha,
            double beta,
            SearchContext context) {
        double terminal = SearchSupport.terminalValue(state.status(), perspective);
        if (!Double.isNaN(terminal)) {
            return terminal;
        }
        if (depth <= 0 || timedOut(context.deadline())) {
            if (shouldQuiesce(state, perspective)) {
                return quiescence(state, perspective, alpha, beta, context, QUIESCENCE_DEPTH);
            }
            return evaluate(state, perspective);
        }

        // 本次思考内的置换表，缓存已经完整搜索过的局面，减少重复子树计算。
        String key = SearchSupport.transpositionKey(state, perspective);
        CacheEntry cached = context.cache().get(key);
        if (cached != null && cached.depth() >= depth) {
            return cached.score();
        }

        List<Move> legalMoves = SearchSupport.fastSafetyFilteredMoves(
                state, engine.legalMoves(state), state.currentTurn(), engine);
        if (legalMoves.isEmpty()) {
            return state.currentTurn() == perspective
                    ? -SearchSupport.WIN_SCORE
                    : SearchSupport.WIN_SCORE;
        }

        boolean maximizing = state.currentTurn() == perspective;
        double best = maximizing ? -Double.MAX_VALUE : Double.MAX_VALUE;
        boolean searchedAny = false;
        boolean cutoff = false;
        boolean completed = true;
        for (Move move : orderedMoves(state, legalMoves, perspective)) {
            if (timedOut(context.deadline())) {
                completed = false;
                break;
            }
            double score = expectedMoveValue(
                    state,
                    move,
                    perspective,
                    depth - 1,
                    alpha,
                    beta,
                    context);
            if (Double.isNaN(score)) {
                completed = false;
                break;
            }
            if (maximizing) {
                best = Math.max(best, score);
                alpha = Math.max(alpha, best);
            } else {
                best = Math.min(best, score);
                beta = Math.min(beta, best);
            }
            searchedAny = true;
            if (beta <= alpha) {
                cutoff = true;
                break;
            }
        }
        if (!searchedAny) {
            return evaluate(state, perspective);
        }
        if (!completed) {
            return Double.NaN;
        }
        if (completed && !cutoff && context.cache().size() < MAX_TRANSPOSITION_ENTRIES) {
            context.cache().put(key, new CacheEntry(depth, best));
        }
        return best;
    }

    private double expectedMoveValue(
            GameState state,
            Move move,
            Color perspective,
            int depth,
            double alpha,
            double beta,
            SearchContext context) {
        List<SearchSupport.WeightedState> outcomes = SearchSupport.outcomesAfter(state, move, engine);
        if (outcomes.isEmpty()) {
            return -SearchSupport.WIN_SCORE;
        }

        double expected = 0.0;
        boolean chanceNode = outcomes.size() > 1;
        // 翻暗子是概率节点，不能用父节点窗口随意剪枝，否则期望值会被算偏。
        double childAlpha = chanceNode ? -SearchSupport.WIN_SCORE : alpha;
        double childBeta = chanceNode ? SearchSupport.WIN_SCORE : beta;
        for (SearchSupport.WeightedState outcome : outcomes) {
            if (timedOut(context.deadline())) {
                return Double.NaN;
            }
            double childValue = value(outcome.state(), perspective, depth, childAlpha, childBeta, context);
            if (Double.isNaN(childValue)) {
                return Double.NaN;
            }
            expected += childValue * outcome.probability();
        }
        return expected;
    }

    private double quiescence(
            GameState state,
            Color perspective,
            double alpha,
            double beta,
            SearchContext context,
            int quietDepth) {
        double terminal = SearchSupport.terminalValue(state.status(), perspective);
        if (!Double.isNaN(terminal)) {
            return terminal;
        }

        double standPat = evaluate(state, perspective);
        if (timedOut(context.deadline())) {
            return standPat;
        }
        if (quietDepth <= 0) {
            return standPat;
        }

        // 安静搜索只在战术局面继续展开，避免叶子节点刚好停在吃子/将军的混乱状态。
        boolean maximizing = state.currentTurn() == perspective;
        if (maximizing) {
            alpha = Math.max(alpha, standPat);
        } else {
            beta = Math.min(beta, standPat);
        }
        if (beta <= alpha) {
            return standPat;
        }

        boolean sideToMoveInCheck = SearchSupport.hasImmediateKingCapture(
                state, state.currentTurn().opposite(), state.currentTurn(), engine);
        // 被将军时必须展开全部安全应手；平稳局面只展开吃子和将军等战术走法。
        List<Move> safeMoves = SearchSupport.fastSafetyFilteredMoves(
                state, engine.legalMoves(state), state.currentTurn(), engine);
        List<Move> tacticalMoves = sideToMoveInCheck
                ? safeMoves
                : safeMoves.stream()
                        .filter(move -> isTacticalMove(state, move, perspective))
                        .toList();
        double best = standPat;
        for (Move move : orderedMoves(state, tacticalMoves, perspective)) {
            if (timedOut(context.deadline())) {
                break;
            }
            double score = expectedQuiescenceMoveValue(
                    state,
                    move,
                    perspective,
                    alpha,
                    beta,
                    context,
                    quietDepth - 1);
            if (maximizing) {
                best = Math.max(best, score);
                alpha = Math.max(alpha, best);
            } else {
                best = Math.min(best, score);
                beta = Math.min(beta, best);
            }
            if (beta <= alpha) {
                break;
            }
        }
        return best;
    }

    private double expectedQuiescenceMoveValue(
            GameState state,
            Move move,
            Color perspective,
            double alpha,
            double beta,
            SearchContext context,
            int quietDepth) {
        List<SearchSupport.WeightedState> outcomes = SearchSupport.outcomesAfter(state, move, engine);
        if (outcomes.isEmpty()) {
            return -SearchSupport.WIN_SCORE;
        }

        double expected = 0.0;
        boolean chanceNode = outcomes.size() > 1;
        // 安静搜索里的暗子概率也要完整求期望，保持和主搜索同样的信息规则。
        double childAlpha = chanceNode ? -SearchSupport.WIN_SCORE : alpha;
        double childBeta = chanceNode ? SearchSupport.WIN_SCORE : beta;
        for (SearchSupport.WeightedState outcome : outcomes) {
            if (timedOut(context.deadline())) {
                return evaluate(state, perspective);
            }
            expected += quiescence(
                    outcome.state(),
                    perspective,
                    childAlpha,
                    childBeta,
                    context,
                    quietDepth) * outcome.probability();
        }
        return expected;
    }

    private boolean shouldQuiesce(GameState state, Color perspective) {
        return SearchSupport.hasImmediateKingCapture(state, state.currentTurn().opposite(), state.currentTurn(), engine)
                || SearchSupport.hasImmediateKingCapture(
                        state, state.currentTurn(), state.currentTurn().opposite(), engine)
                || SearchSupport.hasImmediateKingCapture(state, perspective.opposite(), perspective, engine);
    }

    private boolean isTacticalMove(GameState state, Move move, Color perspective) {
        Piece target = state.board().pieceAt(move.destination()).orElse(null);
        if (target != null && target.owner() != state.currentTurn()) {
            return true;
        }
        return SearchSupport.outcomesAfter(state, move, engine).stream()
                .anyMatch(outcome -> SearchSupport.hasImmediateKingCapture(
                        outcome.state(),
                        state.currentTurn(),
                        state.currentTurn().opposite(),
                        engine)
                        || SearchSupport.hasImmediateKingCapture(
                                outcome.state(),
                                perspective.opposite(),
                                perspective,
                                engine));
    }

    private List<Move> orderedMoves(GameState state, List<Move> moves, Color perspective) {
        return moves.stream()
                .sorted(Comparator.comparingDouble((Move move) -> moveOrderingScore(state, move, perspective))
                        .reversed())
                .toList();
    }

    private double moveOrderingScore(GameState state, Move move, Color perspective) {
        Piece source = state.board().pieceAt(move.source()).orElse(null);
        Piece target = state.board().pieceAt(move.destination()).orElse(null);
        double score = 0.0;
        if (target != null && target.owner() != state.currentTurn()) {
            score += target.visible()
                    ? target.actualType().baseValue() * 10.0
                    : SearchSupport.expectedHiddenValue(state.hiddenPool(target.owner())) * 8.0;
            if (target.visible() && target.actualType() == PieceType.KING) {
                score += SearchSupport.WIN_SCORE;
            }
            if (source != null && source.visible()) {
                score -= source.actualType().baseValue() * 0.2;
            }
        }

        GameState outcome = SearchSupport.approximateStateAfterMove(state, move);
        if (outcome != null) {
            if (outcome.status() != state.status()) {
                double terminal = SearchSupport.terminalValue(outcome.status(), perspective);
                if (!Double.isNaN(terminal)) {
                    score += terminal;
                }
            }
            if (SearchSupport.hasImmediateKingCapture(
                    outcome,
                    state.currentTurn(),
                    state.currentTurn().opposite(),
                    engine)) {
                score += 50_000.0;
            }
            if (SearchSupport.hasImmediateKingCapture(
                    outcome,
                    perspective.opposite(),
                    perspective,
                    engine)) {
                score -= 80_000.0;
            }
        }
        return score;
    }

    private int adjustedDepth(PlayerView view, SearchBudget budget) {
        int depth = budget.maxDepth();
        long visiblePieces = view.board().pieces().values().stream()
                .filter(Piece::visible)
                .count();
        // 平时保持快速响应；只有被将、能立即取胜或残局时才临时加深搜索。
        boolean underImmediateThreat = SearchSupport.canCaptureVisibleKing(
                view, view.perspective().opposite(), view.perspective());
        boolean canWinImmediately = SearchSupport.canCaptureVisibleKing(
                view, view.perspective(), view.perspective().opposite());
        boolean endgame = view.board().pieces().size() <= 12 || visiblePieces >= 18;
        if ((underImmediateThreat || canWinImmediately || endgame) && depth < 3) {
            return 3;
        }
        return depth;
    }

    private void rememberRevealedPieces(PlayerView view) {
        String observationKey = observationKey(view);
        if (previousObservationKey != null
                && looksLikeOpeningAfterRestart(view)
                && !observationKey.equals(previousObservationKey)) {
            clearMemory();
        }
        previousObservationKey = observationKey;

        Map<Color, EnumMap<PieceType, Integer>> currentVisible = new EnumMap<>(Color.class);
        for (Color color : Color.values()) {
            currentVisible.put(color, new EnumMap<>(PieceType.class));
        }
        // 记住曾经公开出现过的非将帅棋子，被吃掉后也要从后续暗子概率池中扣除。
        for (Piece piece : view.board().pieces().values()) {
            if (!piece.visible() || piece.actualType() == PieceType.KING) {
                continue;
            }
            currentVisible.get(piece.owner()).merge(piece.actualType(), 1, Integer::sum);
        }
        for (Color color : Color.values()) {
            EnumMap<PieceType, Integer> counts = revealedMemory.get(color);
            EnumMap<PieceType, Integer> previousVisible = previousVisibleCounts.get(color);
            for (PieceType type : PieceType.values()) {
                if (type == PieceType.KING) {
                    continue;
                }
                int visibleCount = currentVisible.get(color).getOrDefault(type, 0);
                int previousCount = previousVisible.getOrDefault(type, 0);
                int newlyVisible = Math.max(0, visibleCount - previousCount);
                if (newlyVisible > 0) {
                    rememberNewlyVisible(counts, type, newlyVisible);
                }
            }
            previousVisible.clear();
            previousVisible.putAll(currentVisible.get(color));
        }
    }

    private void rememberNewlyVisible(
            EnumMap<PieceType, Integer> counts,
            PieceType type,
            int newlyVisible) {
        int limit = HiddenPiecePool.standard().count(type);
        counts.merge(type, newlyVisible, (known, added) -> Math.min(limit, known + added));
    }

    private boolean looksLikeOpeningAfterRestart(PlayerView view) {
        return view.board().pieces().size() == 32
                && view.board().pieceAt(Position.parse("e0"))
                        .filter(piece -> piece.owner() == Color.RED)
                        .filter(Piece::visible)
                        .filter(piece -> piece.actualType() == PieceType.KING)
                        .isPresent()
                && view.board().pieceAt(Position.parse("e9"))
                        .filter(piece -> piece.owner() == Color.BLACK)
                        .filter(Piece::visible)
                        .filter(piece -> piece.actualType() == PieceType.KING)
                        .isPresent()
                && view.board().pieces().values().stream()
                        .filter(Piece::visible)
                        .count() <= 4;
    }

    private void clearMemory() {
        for (EnumMap<PieceType, Integer> counts : revealedMemory.values()) {
            counts.clear();
        }
        for (EnumMap<PieceType, Integer> counts : previousVisibleCounts.values()) {
            counts.clear();
        }
    }

    private double evaluate(GameState state, Color perspective) {
        return evaluator.evaluate(
                SearchSupport.viewFrom(state, perspective, engine),
                state.redHiddenPool(),
                state.blackHiddenPool());
    }

    private String observationKey(PlayerView view) {
        return SearchSupport.transpositionKey(SearchSupport.stateFrom(view), view.perspective());
    }

    private boolean timedOut(Instant deadline) {
        return !Instant.now().isBefore(deadline);
    }

    private record SearchContext(Instant deadline, Map<String, CacheEntry> cache) {
        SearchContext(Instant deadline) {
            this(deadline, new HashMap<>());
        }
    }

    private record CacheEntry(int depth, double score) {
    }
}
