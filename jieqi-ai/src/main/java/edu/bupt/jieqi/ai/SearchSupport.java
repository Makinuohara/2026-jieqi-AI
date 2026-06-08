package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.Board;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.HiddenPiecePool;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.model.Position;
import edu.bupt.jieqi.rules.GameEngine;
import edu.bupt.jieqi.rules.ApplyResult;
import edu.bupt.jieqi.rules.StandardGameEngine;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

final class SearchSupport {
    static final double WIN_SCORE = 1_000_000.0;
    private static final GameEngine DEFAULT_ENGINE = new StandardGameEngine();

    private SearchSupport() {
    }

    static GameState stateFrom(PlayerView view) {
        return new GameState(
                view.board(),
                view.currentTurn(),
                0,
                0, 0, null, null,
                0, 0, null, null,
                0,
                GameStatus.PLAYING,
                inferHiddenPool(view.board(), Color.RED),
                inferHiddenPool(view.board(), Color.BLACK));
    }

    static GameState stateFrom(PlayerView view, HiddenPiecePool redPool, HiddenPiecePool blackPool) {
        return new GameState(
                view.board(),
                view.currentTurn(),
                0,
                0, 0, null, null,
                0, 0, null, null,
                0,
                GameStatus.PLAYING,
                redPool,
                blackPool);
    }

    static PlayerView viewFrom(GameState state, Color perspective, GameEngine engine) {
        return PlayerView.from(state, perspective, engine.legalMoves(state));
    }

    static HiddenPiecePool inferHiddenPool(Board board, Color owner) {
        EnumMap<PieceType, Integer> counts = new EnumMap<>(HiddenPiecePool.standard().counts());
        for (Piece piece : board.pieces().values()) {
            if (piece.owner() == owner && piece.visible() && piece.actualType() != PieceType.KING) {
                counts.computeIfPresent(piece.actualType(), (ignored, count) -> Math.max(0, count - 1));
            }
        }
        return new HiddenPiecePool(counts);
    }

    static HiddenPiecePool inferHiddenPool(
            Board board,
            Color owner,
            Map<PieceType, Integer> knownRevealedCounts) {
        EnumMap<PieceType, Integer> counts = new EnumMap<>(HiddenPiecePool.standard().counts());
        for (Piece piece : board.pieces().values()) {
            if (piece.owner() == owner && piece.visible() && piece.actualType() != PieceType.KING) {
                counts.computeIfPresent(piece.actualType(), (ignored, count) -> Math.max(0, count - 1));
            }
        }
        for (Map.Entry<PieceType, Integer> entry : knownRevealedCounts.entrySet()) {
            if (entry.getKey() == PieceType.KING) {
                continue;
            }
            int remembered = Math.max(0, entry.getValue());
            int visible = visibleCount(board, owner, entry.getKey());
            int capturedOrMovedOutOfSight = Math.max(0, remembered - visible);
            // 已经公开过但当前不可见的棋子，通常是被吃掉了，不能再算进暗子池。
            counts.computeIfPresent(entry.getKey(),
                    (ignored, count) -> Math.max(0, count - capturedOrMovedOutOfSight));
        }
        return new HiddenPiecePool(counts);
    }

    private static int visibleCount(Board board, Color owner, PieceType type) {
        int count = 0;
        for (Piece piece : board.pieces().values()) {
            if (piece.owner() == owner
                    && piece.visible()
                    && piece.actualType() == type) {
                count++;
            }
        }
        return count;
    }

    static double expectedHiddenValue(HiddenPiecePool pool) {
        int total = pool.total();
        if (total == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (Map.Entry<PieceType, Integer> entry : pool.counts().entrySet()) {
            sum += entry.getKey().baseValue() * entry.getValue();
        }
        return sum / total;
    }

    static double terminalValue(GameStatus status, Color perspective) {
        return switch (status) {
            case RED_WIN -> perspective == Color.RED ? WIN_SCORE : -WIN_SCORE;
            case BLACK_WIN -> perspective == Color.BLACK ? WIN_SCORE : -WIN_SCORE;
            case DRAW -> 0.0;
            case WAITING, PLAYING -> Double.NaN;
        };
    }

    static Position visibleKingPosition(Board board, Color owner) {
        return board.pieces().entrySet().stream()
                .filter(entry -> entry.getValue().owner() == owner)
                .filter(entry -> entry.getValue().visible())
                .filter(entry -> entry.getValue().actualType() == PieceType.KING)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    static boolean canCaptureVisibleKing(PlayerView view, Color attacker, Color kingOwner) {
        return hasImmediateKingCapture(stateFrom(view), attacker, kingOwner, DEFAULT_ENGINE);
    }

    static boolean hasImmediateKingCapture(
            GameState state,
            Color attacker,
            Color kingOwner,
            GameEngine engine) {
        Position king = visibleKingPosition(state.board(), kingOwner);
        if (king == null || state.status() != GameStatus.PLAYING) {
            return false;
        }
        // 热路径：直接判断棋子是否攻击帅位，比生成一整张合法走法表更快。
        return state.board().pieces().entrySet().stream()
                .filter(entry -> entry.getValue().owner() == attacker)
                .anyMatch(entry -> canAttack(state.board(), entry.getKey(), king, entry.getValue()));
    }

    static List<Move> safetyFilteredMoves(
            GameState state,
            List<Move> legalMoves,
            Color mover,
            GameEngine engine) {
        // 严格版本会枚举暗子翻开后的所有可能结果，适合测试和高风险局面。
        List<Move> safeMoves = safeMoves(state, legalMoves, mover, engine);
        return safeMoves.isEmpty() ? legalMoves : safeMoves;
    }

    static List<Move> fastSafetyFilteredMoves(
            GameState state,
            List<Move> legalMoves,
            Color mover,
            GameEngine engine) {
        // 交互对局使用快速版本：不展开暗子概率，只检查走完后是否直接送将帅。
        List<Move> safeMoves = legalMoves.stream()
                .filter(move -> isKingSafeAfterMoveFast(state, move, mover, engine))
                .collect(Collectors.toList());
        return safeMoves.isEmpty() ? legalMoves : safeMoves;
    }

    static List<Move> safeMoves(
            GameState state,
            List<Move> legalMoves,
            Color mover,
            GameEngine engine) {
        return legalMoves.stream()
                .filter(move -> isKingSafeAfterMove(state, move, mover, engine))
                .collect(Collectors.toList());
    }

    static boolean isKingSafeAfterMove(
            GameState state,
            Move move,
            Color kingOwner,
            GameEngine engine) {
        // 暗子移动会随机揭示实际类型；任一揭示结果会送将帅，都视为不安全。
        List<WeightedState> outcomes = outcomesAfter(state, move, engine);
        if (outcomes.isEmpty()) {
            return false;
        }
        for (WeightedState outcome : outcomes) {
            GameState next = outcome.state();
            if (next.status() != GameStatus.PLAYING) {
                continue;
            }
            if (hasImmediateKingCapture(next, kingOwner.opposite(), kingOwner, engine)) {
                return false;
            }
        }
        return true;
    }

    static boolean isKingSafeAfterMoveFast(
            GameState state,
            Move move,
            Color kingOwner,
            GameEngine engine) {
        // 快速检查只关心棋盘占位变化，避免在每个候选步上展开所有翻子概率。
        GameState next = approximateStateAfterMove(state, move);
        if (next == null || next.status() != GameStatus.PLAYING) {
            return next != null;
        }
        return !hasImmediateKingCapture(next, kingOwner.opposite(), kingOwner, engine);
    }

    static GameState approximateStateAfterMove(GameState state, Move move) {
        Piece source = state.board().pieceAt(move.source()).orElse(null);
        if (source == null || source.owner() != state.currentTurn()) {
            return null;
        }
        Piece target = state.board().pieceAt(move.destination()).orElse(null);
        if (target != null && target.owner() == source.owner()) {
            return null;
        }

        Board nextBoard = state.board().move(move.source(), move.destination(), source);
        GameStatus status = winnerAfterCapture(source.owner(), target);
        return new GameState(
                nextBoard,
                state.currentTurn().opposite(),
                target == null ? state.noCaptureHalfMoves() + 1 : 0,
                state.redConsecutiveCheckCount(),
                state.redConsecutiveChaseCount(),
                state.redChasedPosition(),
                state.redChasedPieceType(),
                state.blackConsecutiveCheckCount(),
                state.blackConsecutiveChaseCount(),
                state.blackChasedPosition(),
                state.blackChasedPieceType(),
                state.turnStartedAt(),
                status,
                state.redHiddenPool(),
                state.blackHiddenPool());
    }

    static List<WeightedState> outcomesAfter(GameState state, Move move, GameEngine engine) {
        Piece source = state.board().pieceAt(move.source()).orElse(null);
        if (source == null) {
            return List.of();
        }
        if (source.visible()) {
            return singleOutcome(engine, state, move);
        }

        HiddenPiecePool pool = state.hiddenPool(source.owner());
        if (pool.total() == 0) {
            return singleOutcome(engine, state, move);
        }

        // 每种可能翻出的棋子类型都是一个 chance node 分支，权重来自剩余暗子池。
        java.util.ArrayList<WeightedState> outcomes = new java.util.ArrayList<>();
        for (PieceType type : PieceType.values()) {
            int count = pool.count(type);
            if (count == 0) {
                continue;
            }
            StandardGameEngine fixedEngine = new StandardGameEngine(
                    new FixedDrawRandom(firstDrawIndex(pool, type)));
            ApplyResult result = fixedEngine.apply(state, move);
            if (result.validation().valid()) {
                outcomes.add(new WeightedState(result.state(), (double) count / pool.total()));
            }
        }
        return List.copyOf(outcomes);
    }

    private static List<WeightedState> singleOutcome(
            GameEngine engine,
            GameState state,
            Move move) {
        ApplyResult result = engine.apply(state, move);
        if (!result.validation().valid()) {
            return List.of();
        }
        return List.of(new WeightedState(result.state(), 1.0));
    }

    static double immediateCaptureValue(PlayerView view, Color attacker) {
        return immediateCaptureValue(
                view,
                attacker,
                inferHiddenPool(view.board(), Color.RED),
                inferHiddenPool(view.board(), Color.BLACK));
    }

    static double immediateCaptureValue(
            PlayerView view,
            Color attacker,
            HiddenPiecePool redPool,
            HiddenPiecePool blackPool) {
        GameState state = withTurn(stateFrom(view, redPool, blackPool), attacker);
        List<Move> legalMoves = DEFAULT_ENGINE.legalMoves(state);
        double best = 0.0;
        for (Move move : legalMoves) {
            Piece target = view.board().pieceAt(move.destination()).orElse(null);
            if (target == null || target.owner() == attacker) {
                continue;
            }
            double value = target.visible()
                    ? target.actualType().baseValue()
                    : expectedHiddenValue(hiddenPoolFor(target.owner(), redPool, blackPool));
            best = Math.max(best, value);
        }
        return best;
    }

    private static HiddenPiecePool hiddenPoolFor(
            Color owner,
            HiddenPiecePool redPool,
            HiddenPiecePool blackPool) {
        return owner == Color.RED ? redPool : blackPool;
    }

    static GameState withTurn(GameState state, Color currentTurn) {
        return new GameState(
                state.board(),
                currentTurn,
                state.noCaptureHalfMoves(),
                state.redConsecutiveCheckCount(),
                state.redConsecutiveChaseCount(),
                state.redChasedPosition(),
                state.redChasedPieceType(),
                state.blackConsecutiveCheckCount(),
                state.blackConsecutiveChaseCount(),
                state.blackChasedPosition(),
                state.blackChasedPieceType(),
                state.turnStartedAt(),
                state.status(),
                state.redHiddenPool(),
                state.blackHiddenPool());
    }

    static String transpositionKey(GameState state, Color perspective) {
        StringBuilder key = new StringBuilder(256);
        // 局面缓存键必须包含轮到谁、公开棋盘和剩余暗子池，否则不同概率局面会混淆。
        key.append("p=").append(perspective)
                .append(";t=").append(state.currentTurn())
                .append(";s=").append(state.status())
                .append(";n=").append(state.noCaptureHalfMoves())
                .append(";rc=").append(state.redConsecutiveCheckCount())
                .append(",").append(state.redConsecutiveChaseCount())
                .append(";bc=").append(state.blackConsecutiveCheckCount())
                .append(",").append(state.blackConsecutiveChaseCount())
                .append(";rp=").append(poolKey(state.redHiddenPool()))
                .append(";bp=").append(poolKey(state.blackHiddenPool()))
                .append(";b=");
        for (int rank = 0; rank <= 9; rank++) {
            for (int file = 0; file <= 8; file++) {
                Position position = new Position(file, rank);
                Piece piece = state.board().pieceAt(position).orElse(null);
                if (piece == null) {
                    key.append('.');
                    continue;
                }
                key.append(piece.owner() == Color.RED ? 'r' : 'b')
                        .append(piece.virtualType().ordinal())
                        .append(piece.visible() ? 'v' : 'h');
                if (piece.visible()) {
                    key.append(piece.actualType().ordinal());
                }
                key.append('@');
            }
        }
        return key.toString();
    }

    private static String poolKey(HiddenPiecePool pool) {
        StringBuilder key = new StringBuilder();
        for (PieceType type : PieceType.values()) {
            key.append(type.ordinal()).append(':').append(pool.count(type)).append(',');
        }
        return key.toString();
    }

    private static GameStatus winnerAfterCapture(Color mover, Piece captured) {
        if (captured == null
                || !captured.visible()
                || captured.actualType() != PieceType.KING) {
            return GameStatus.PLAYING;
        }
        return mover == Color.RED ? GameStatus.RED_WIN : GameStatus.BLACK_WIN;
    }

    private static boolean canAttack(
            Board board, Position source, Position destination, Piece piece) {
        Piece target = board.pieceAt(destination).orElse(null);
        if (target != null && target.owner() == piece.owner()) {
            return false;
        }

        int dx = destination.file() - source.file();
        int dy = destination.rank() - source.rank();
        // 复制规则层的基础攻击判定，供 AI 高频安全检查使用。
        return switch (piece.movementType()) {
            case ROOK -> isStraight(dx, dy) && countBetween(board, source, destination) == 0;
            case KNIGHT -> isKnight(board, source, dx, dy);
            case CANNON -> isCannon(board, source, destination, target);
            case PAWN -> isPawn(piece.owner(), source, dx, dy);
            case KING -> isKing(piece.owner(), destination, dx, dy);
            case GUARD -> Math.abs(dx) == 1 && Math.abs(dy) == 1;
            case BISHOP -> isBishop(board, source, dx, dy);
        };
    }

    private static boolean isKnight(Board board, Position source, int dx, int dy) {
        if (!((Math.abs(dx) == 1 && Math.abs(dy) == 2)
                || (Math.abs(dx) == 2 && Math.abs(dy) == 1))) {
            return false;
        }
        Position leg = Math.abs(dx) == 2
                ? new Position(source.file() + Integer.signum(dx), source.rank())
                : new Position(source.file(), source.rank() + Integer.signum(dy));
        return board.pieceAt(leg).isEmpty();
    }

    private static boolean isCannon(
            Board board, Position source, Position destination, Piece target) {
        int dx = destination.file() - source.file();
        int dy = destination.rank() - source.rank();
        if (!isStraight(dx, dy)) {
            return false;
        }
        int between = countBetween(board, source, destination);
        return target == null ? between == 0 : between == 1;
    }

    private static boolean isPawn(Color owner, Position source, int dx, int dy) {
        int forward = owner == Color.RED ? 1 : -1;
        if (dx == 0 && dy == forward) {
            return true;
        }
        boolean crossedRiver = owner == Color.RED ? source.rank() >= 5 : source.rank() <= 4;
        return crossedRiver && Math.abs(dx) == 1 && dy == 0;
    }

    private static boolean isKing(Color owner, Position destination, int dx, int dy) {
        if (Math.abs(dx) + Math.abs(dy) != 1) {
            return false;
        }
        int minimumRank = owner == Color.RED ? 0 : 7;
        int maximumRank = owner == Color.RED ? 2 : 9;
        return destination.file() >= 3
                && destination.file() <= 5
                && destination.rank() >= minimumRank
                && destination.rank() <= maximumRank;
    }

    private static boolean isBishop(Board board, Position source, int dx, int dy) {
        if (Math.abs(dx) != 2 || Math.abs(dy) != 2) {
            return false;
        }
        Position eye = new Position(
                source.file() + dx / 2,
                source.rank() + dy / 2);
        return board.pieceAt(eye).isEmpty();
    }

    private static int countBetween(Board board, Position source, Position destination) {
        int stepFile = Integer.signum(destination.file() - source.file());
        int stepRank = Integer.signum(destination.rank() - source.rank());
        int file = source.file() + stepFile;
        int rank = source.rank() + stepRank;
        int count = 0;
        while (file != destination.file() || rank != destination.rank()) {
            if (board.pieceAt(new Position(file, rank)).isPresent()) {
                count++;
            }
            file += stepFile;
            rank += stepRank;
        }
        return count;
    }

    private static boolean isStraight(int dx, int dy) {
        return (dx == 0) != (dy == 0);
    }

    static record WeightedState(GameState state, double probability) {
    }

    static int firstDrawIndex(HiddenPiecePool pool, PieceType desiredType) {
        int index = 0;
        for (PieceType type : PieceType.values()) {
            int count = pool.count(type);
            if (type == desiredType) {
                if (count <= 0) {
                    throw new IllegalArgumentException("No hidden piece of type " + desiredType + " remains");
                }
                return index;
            }
            index += count;
        }
        throw new IllegalArgumentException("No hidden piece of type " + desiredType + " remains");
    }

    static final class FixedDrawRandom implements RandomGenerator {
        private final int selectedIndex;

        FixedDrawRandom(int selectedIndex) {
            this.selectedIndex = selectedIndex;
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            return Math.min(selectedIndex, bound - 1);
        }

        @Override
        public long nextLong() {
            return selectedIndex;
        }
    }
}
