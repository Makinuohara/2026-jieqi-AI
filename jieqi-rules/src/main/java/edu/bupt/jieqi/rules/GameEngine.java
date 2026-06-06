package edu.bupt.jieqi.rules;

import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.Move;

public interface GameEngine {
    ApplyResult apply(GameState state, Move move);
}

