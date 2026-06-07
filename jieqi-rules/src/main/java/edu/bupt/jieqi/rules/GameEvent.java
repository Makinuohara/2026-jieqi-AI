package edu.bupt.jieqi.rules;

import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.PieceType;

public sealed interface GameEvent permits
        GameEvent.PieceRevealed, GameEvent.TurnChanged, GameEvent.GameEnded {
    record PieceRevealed(PieceType type) implements GameEvent {
    }

    record TurnChanged(Color currentTurn) implements GameEvent {
    }

    record GameEnded(GameStatus status) implements GameEvent {
    }
}
