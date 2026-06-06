package edu.bupt.jieqi.model;

import java.util.Objects;

public record GameState(
        Board board,
        Color currentTurn,
        int noCaptureHalfMoves,
        int consecutiveCheckCount,
        int consecutiveChaseCount,
        long turnStartedAt,
        GameStatus status) {

    public GameState {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(currentTurn, "currentTurn");
        Objects.requireNonNull(status, "status");
        if (noCaptureHalfMoves < 0 || consecutiveCheckCount < 0 || consecutiveChaseCount < 0) {
            throw new IllegalArgumentException("Counters cannot be negative");
        }
    }

    public static GameState initial() {
        return new GameState(Board.initial(), Color.RED, 0, 0, 0,
                System.currentTimeMillis(), GameStatus.PLAYING);
    }
}

