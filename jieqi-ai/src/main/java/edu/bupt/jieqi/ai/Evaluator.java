package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.PlayerView;

@FunctionalInterface
public interface Evaluator {
    double evaluate(PlayerView view);
}

