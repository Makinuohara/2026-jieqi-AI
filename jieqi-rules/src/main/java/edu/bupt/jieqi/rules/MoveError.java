package edu.bupt.jieqi.rules;

public enum MoveError {
    NONE,
    FLIP_IN_PLACE_FORBIDDEN,
    SOURCE_EMPTY,
    NOT_YOUR_PIECE,
    FRIENDLY_DESTINATION,
    ILLEGAL_PIECE_MOVEMENT,
    NOT_YOUR_TURN,
    GAME_NOT_ACTIVE,
    TIMEOUT
}

