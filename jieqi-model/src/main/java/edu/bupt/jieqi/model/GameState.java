package edu.bupt.jieqi.model;

import java.util.Objects;

public record GameState(
        Board board,
        Color currentTurn,
        int noCaptureHalfMoves,
        int consecutiveCheckCount,
        int consecutiveChaseCount,
        Color consecutiveCheckOwner,
        Color consecutiveChaseOwner,
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
        if (noCaptureHalfMoves < 0 || consecutiveCheckCount < 0 || consecutiveChaseCount < 0) {
            throw new IllegalArgumentException("Counters cannot be negative");
        }
    }

    public GameState(
            Board board,
            Color currentTurn,
            int noCaptureHalfMoves,
            int consecutiveCheckCount,
            int consecutiveChaseCount,
            long turnStartedAt,
            GameStatus status,
            HiddenPiecePool redHiddenPool,
            HiddenPiecePool blackHiddenPool) {
        this(
                board,
                currentTurn,
                noCaptureHalfMoves,
                consecutiveCheckCount,
                consecutiveChaseCount,
                null,
                null,
                turnStartedAt,
                status,
                redHiddenPool,
                blackHiddenPool);
    }

    public static GameState initial() {
        return new GameState(Board.initial(), Color.RED, 0, 0, 0, null, null,
                System.currentTimeMillis(), GameStatus.PLAYING,
                HiddenPiecePool.standard(), HiddenPiecePool.standard());
    }

    public HiddenPiecePool hiddenPool(Color color) {
        return color == Color.RED ? redHiddenPool : blackHiddenPool;
    }
}
