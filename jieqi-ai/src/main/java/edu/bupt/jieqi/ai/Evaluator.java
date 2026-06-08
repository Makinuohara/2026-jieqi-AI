package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.HiddenPiecePool;
import edu.bupt.jieqi.model.PlayerView;

@FunctionalInterface
public interface Evaluator {
    double evaluate(PlayerView view);

    default double evaluate(PlayerView view, HiddenPiecePool redPool, HiddenPiecePool blackPool) {
        return evaluate(view);
    }
}
