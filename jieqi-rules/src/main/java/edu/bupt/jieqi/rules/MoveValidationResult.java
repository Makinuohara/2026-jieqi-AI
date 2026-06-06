package edu.bupt.jieqi.rules;

public record MoveValidationResult(boolean valid, MoveError error, String message) {
    public static MoveValidationResult accepted() {
        return new MoveValidationResult(true, MoveError.NONE, "ok");
    }

    public static MoveValidationResult rejected(MoveError error, String message) {
        return new MoveValidationResult(false, error, message);
    }
}

