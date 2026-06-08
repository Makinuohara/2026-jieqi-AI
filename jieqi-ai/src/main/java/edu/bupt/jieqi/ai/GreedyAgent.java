package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.HiddenPiecePool;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.rules.ApplyResult;
import edu.bupt.jieqi.rules.GameEngine;
import edu.bupt.jieqi.rules.StandardGameEngine;
import java.util.List;
import java.util.Objects;

public final class GreedyAgent implements Agent {
    private final Evaluator evaluator;
    private final GameEngine engine;

    public GreedyAgent() {
        this(new MaterialEvaluator(), new StandardGameEngine());
    }

    public GreedyAgent(Evaluator evaluator) {
        this(evaluator, new StandardGameEngine());
    }

    public GreedyAgent(Evaluator evaluator, GameEngine engine) {
        this.evaluator = Objects.requireNonNull(evaluator);
        this.engine = Objects.requireNonNull(engine);
    }

    @Override
    public Move chooseMove(PlayerView view, SearchBudget budget) {
        GameState state = SearchSupport.stateFrom(view);
        List<Move> candidates = SearchSupport.safetyFilteredMoves(
                state, view.legalMoves(), view.perspective(), engine);
        return candidates.stream()
                .max((left, right) -> Double.compare(score(view, left), score(view, right)))
                .orElseThrow(() -> new IllegalStateException("No legal move is available"));
    }

    @Override
    public String name() {
        return "Greedy";
    }

    private double score(PlayerView view, Move move) {
        GameState state = SearchSupport.stateFrom(view);
        Piece source = state.board().pieceAt(move.source()).orElseThrow();
        if (!source.visible()) {
            return expectedRevealScore(state, source, move, view.perspective());
        }

        ApplyResult result = engine.apply(state, move);
        if (!result.validation().valid()) {
            return -SearchSupport.WIN_SCORE;
        }
        double terminal = SearchSupport.terminalValue(result.state().status(), view.perspective());
        if (!Double.isNaN(terminal)) {
            return terminal;
        }
        return evaluate(result.state(), view.perspective());
    }

    private double expectedRevealScore(
            GameState state, Piece source, Move move, edu.bupt.jieqi.model.Color perspective) {
        HiddenPiecePool pool = state.hiddenPool(source.owner());
        if (pool.total() == 0) {
            return -SearchSupport.WIN_SCORE;
        }

        double expected = 0.0;
        for (PieceType type : PieceType.values()) {
            int count = pool.count(type);
            if (count == 0) {
                continue;
            }
            StandardGameEngine fixedEngine = new StandardGameEngine(
                    new SearchSupport.FixedDrawRandom(SearchSupport.firstDrawIndex(pool, type)));
            ApplyResult result = fixedEngine.apply(state, move);
            if (!result.validation().valid()) {
                continue;
            }
            double terminal = SearchSupport.terminalValue(result.state().status(), perspective);
            double value = Double.isNaN(terminal)
                    ? evaluate(result.state(), perspective)
                    : terminal;
            expected += value * count / pool.total();
        }
        return expected;
    }

    private double evaluate(GameState state, edu.bupt.jieqi.model.Color perspective) {
        return evaluator.evaluate(
                SearchSupport.viewFrom(state, perspective, engine),
                state.redHiddenPool(),
                state.blackHiddenPool());
    }
}
