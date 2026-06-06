package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.PlayerView;
import java.util.Objects;

public final class ExpectiminimaxAgent implements Agent {
    private final Evaluator evaluator;
    private final GreedyAgent fallback = new GreedyAgent();

    public ExpectiminimaxAgent(Evaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator);
    }

    @Override
    public Move chooseMove(PlayerView view, SearchBudget budget) {
        // TODO(C/D): expand chance nodes from remaining reveal pools and evaluate child states.
        evaluator.evaluate(view);
        return fallback.chooseMove(view, budget);
    }

    @Override
    public String name() {
        return "Expectiminimax";
    }
}

