package edu.bupt.jieqi.ai;

import java.time.Duration;

public record SearchBudget(Duration timeLimit, int maxDepth) {
    public SearchBudget {
        if (timeLimit == null || timeLimit.isNegative() || timeLimit.isZero()) {
            throw new IllegalArgumentException("Time limit must be positive");
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("Max depth must be at least one");
        }
    }
}

