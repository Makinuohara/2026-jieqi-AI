package edu.bupt.jieqi.rules;

import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.PieceType;

public sealed interface GameEvent permits GameEvent.PieceRevealed, GameEvent.TurnChanged {
    record PieceRevealed(PieceType type) implements GameEvent {
    }

    record TurnChanged(Color currentTurn) implements GameEvent {
    }
}

