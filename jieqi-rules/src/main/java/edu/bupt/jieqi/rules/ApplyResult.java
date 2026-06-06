package edu.bupt.jieqi.rules;

import edu.bupt.jieqi.model.GameState;
import java.util.List;

public record ApplyResult(GameState state, MoveValidationResult validation, List<GameEvent> events) {
    public ApplyResult {
        events = List.copyOf(events);
    }

    public static ApplyResult rejected(GameState state, MoveError error, String message) {
        return new ApplyResult(state, MoveValidationResult.rejected(error, message), List.of());
    }
}

