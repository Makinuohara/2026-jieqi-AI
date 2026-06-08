package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.HiddenPiecePool;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.rules.ApplyResult;
import edu.bupt.jieqi.rules.GameEngine;
import edu.bupt.jieqi.rules.StandardGameEngine;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class ExpectiminimaxAgent implements Agent {
    private final Evaluator evaluator;
    private final GameEngine engine;
    private final GreedyAgent fallback;

    public ExpectiminimaxAgent(Evaluator evaluator) {
        this(evaluator, new StandardGameEngine());
    }

    public ExpectiminimaxAgent(Evaluator evaluator, GameEngine engine) {
        this.evaluator = Objects.requireNonNull(evaluator);
        this.engine = Objects.requireNonNull(engine);
        this.fallback = new GreedyAgent(evaluator, engine);
    }

    @Override
    public Move chooseMove(PlayerView view, SearchBudget budget) {
        if (view.legalMoves().isEmpty()) {
            throw new IllegalStateException("No legal move is available");
        }

        Instant deadline = Instant.now().plus(budget.timeLimit());
        GameState state = SearchSupport.stateFrom(view);
        Color perspective = view.perspective();
        Move bestMove = null;
        double bestScore = -Double.MAX_VALUE;

        for (Move move : view.legalMoves()) {
            if (timedOut(deadline)) {
                break;
            }
            double score = expectedMoveValue(
                    state, move, perspective, budget.maxDepth() - 1, deadline);
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

    private double value(GameState state, Color perspective, int depth, Instant deadline) {
        double terminal = SearchSupport.terminalValue(state.status(), perspective);
        if (!Double.isNaN(terminal)) {
            return terminal;
        }
        if (depth <= 0 || timedOut(deadline)) {
            return evaluator.evaluate(SearchSupport.viewFrom(state, perspective, engine));
        }

        List<Move> legalMoves = engine.legalMoves(state);
        if (legalMoves.isEmpty()) {
            return state.currentTurn() == perspective
                    ? -SearchSupport.WIN_SCORE
                    : SearchSupport.WIN_SCORE;
        }

        boolean maximizing = state.currentTurn() == perspective;
        double best = maximizing ? -Double.MAX_VALUE : Double.MAX_VALUE;
        boolean searchedAny = false;
        for (Move move : legalMoves) {
            if (timedOut(deadline)) {
                break;
            }
            double score = expectedMoveValue(state, move, perspective, depth - 1, deadline);
            best = maximizing ? Math.max(best, score) : Math.min(best, score);
            searchedAny = true;
        }
        return searchedAny ? best : evaluator.evaluate(SearchSupport.viewFrom(state, perspective, engine));
    }

    private double expectedMoveValue(
            GameState state, Move move, Color perspective, int depth, Instant deadline) {
        Piece source = state.board().pieceAt(move.source()).orElse(null);
        if (source == null) {
            return -SearchSupport.WIN_SCORE;
        }
        if (source.visible()) {
            return applyAndEvaluate(engine, state, move, perspective, depth, deadline);
        }

        HiddenPiecePool pool = state.hiddenPool(source.owner());
        if (pool.total() == 0) {
            return applyAndEvaluate(engine, state, move, perspective, depth, deadline);
        }

        double expected = 0.0;
        for (PieceType type : PieceType.values()) {
            int count = pool.count(type);
            if (count == 0) {
                continue;
            }
            StandardGameEngine fixedEngine = new StandardGameEngine(
                    new SearchSupport.FixedDrawRandom(SearchSupport.firstDrawIndex(pool, type)));
            expected += applyAndEvaluate(fixedEngine, state, move, perspective, depth, deadline)
                    * count / pool.total();
        }
        return expected;
    }

    private double applyAndEvaluate(
            GameEngine engineToApply,
            GameState state,
            Move move,
            Color perspective,
            int depth,
            Instant deadline) {
        ApplyResult result = engineToApply.apply(state, move);
        if (!result.validation().valid()) {
            return state.currentTurn() == perspective
                    ? -SearchSupport.WIN_SCORE
                    : SearchSupport.WIN_SCORE;
        }
        return value(result.state(), perspective, depth, deadline);
    }

    private boolean timedOut(Instant deadline) {
        return !Instant.now().isBefore(deadline);
    }
}
