package edu.bupt.jieqi.model;

import java.util.Objects;

public record GameState(
        Board board,
        Color currentTurn,
        int noCaptureHalfMoves,
        int redConsecutiveCheckCount,
        int redConsecutiveChaseCount,
        Position redChasedPosition,
        PieceType redChasedPieceType,
        int blackConsecutiveCheckCount,
        int blackConsecutiveChaseCount,
        Position blackChasedPosition,
        PieceType blackChasedPieceType,
        long turnStartedAt,
        GameStatus status,
        HiddenPiecePool redHiddenPool,
        HiddenPiecePool blackHiddenPool) {

    public GameState {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(currentTurn, "currentTurn");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(redHiddenPool, "redHiddenPool");
        Objects.requireNonNull(blackHiddenPool, "blackHiddenPool");
        if (noCaptureHalfMoves < 0
                || redConsecutiveCheckCount < 0 || redConsecutiveChaseCount < 0
                || blackConsecutiveCheckCount < 0 || blackConsecutiveChaseCount < 0) {
            throw new IllegalArgumentException("Counters cannot be negative");
        }
    }

    public static GameState initial() {
        return new GameState(Board.initial(), Color.RED, 0,
                0, 0, null, null,
                0, 0, null, null,
                System.currentTimeMillis(), GameStatus.PLAYING,
                HiddenPiecePool.standard(), HiddenPiecePool.standard());
    }

    public HiddenPiecePool hiddenPool(Color color) {
        return color == Color.RED ? redHiddenPool : blackHiddenPool;
    }

    /** Get the consecutive check count for the given color. */
    public int consecutiveCheckCount(Color color) {
        return color == Color.RED ? redConsecutiveCheckCount : blackConsecutiveCheckCount;
    }

    /** Get the consecutive chase count for the given color. */
    public int consecutiveChaseCount(Color color) {
        return color == Color.RED ? redConsecutiveChaseCount : blackConsecutiveChaseCount;
    }

    /** Get the chased piece position for the given color. */
    public Position chasedPosition(Color color) {
        return color == Color.RED ? redChasedPosition : blackChasedPosition;
    }

    /** Get the chased piece type for the given color, or null if no chase is active. */
    public PieceType chasedPieceType(Color color) {
        return color == Color.RED ? redChasedPieceType : blackChasedPieceType;
    }
}
