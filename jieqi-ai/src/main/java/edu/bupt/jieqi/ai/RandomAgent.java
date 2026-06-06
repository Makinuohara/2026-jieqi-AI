package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.PlayerView;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class RandomAgent implements Agent {
    private final RandomGenerator random;

    public RandomAgent() {
        this(RandomGenerator.getDefault());
    }

    public RandomAgent(RandomGenerator random) {
        this.random = Objects.requireNonNull(random);
    }

    @Override
    public Move chooseMove(PlayerView view, SearchBudget budget) {
        if (view.legalMoves().isEmpty()) {
            throw new IllegalStateException("No legal move is available");
        }
        return view.legalMoves().get(random.nextInt(view.legalMoves().size()));
    }

    @Override
    public String name() {
        return "Random";
    }
}

