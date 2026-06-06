package edu.bupt.jieqi.model;

import java.util.List;
import java.util.Objects;

public record PlayerView(Board board, Color currentTurn, Color perspective, List<Move> legalMoves) {
    public PlayerView {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(currentTurn, "currentTurn");
        Objects.requireNonNull(perspective, "perspective");
        legalMoves = List.copyOf(legalMoves);
    }

    public static PlayerView from(GameState state, Color perspective, List<Move> legalMoves) {
        return new PlayerView(state.board().publicCopy(), state.currentTurn(), perspective, legalMoves);
    }
}

