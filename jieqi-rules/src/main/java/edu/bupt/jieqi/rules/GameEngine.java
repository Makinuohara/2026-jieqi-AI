package edu.bupt.jieqi.rules;

import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.Move;
import java.util.List;

public interface GameEngine {
    List<Move> legalMoves(GameState state);

    boolean isInCheck(GameState state, Color color);

    ApplyResult apply(GameState state, Move move);
}
