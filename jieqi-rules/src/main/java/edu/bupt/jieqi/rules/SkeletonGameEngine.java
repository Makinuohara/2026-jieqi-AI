package edu.bupt.jieqi.rules;

import edu.bupt.jieqi.model.Board;
import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import java.util.List;

public final class SkeletonGameEngine implements GameEngine {
    @Override
    public ApplyResult apply(GameState state, Move move) {
        if (state.status() != GameStatus.PLAYING) {
            return ApplyResult.rejected(state, MoveError.GAME_NOT_ACTIVE, "Game is not active");
        }
        if (move.source().equals(move.destination())) {
            return ApplyResult.rejected(
                    state, MoveError.FLIP_IN_PLACE_FORBIDDEN, "Flip-in-place is forbidden");
        }

        Piece source = state.board().pieceAt(move.source()).orElse(null);
        if (source == null) {
            return ApplyResult.rejected(state, MoveError.SOURCE_EMPTY, "Source square is empty");
        }
        if (source.owner() != state.currentTurn()) {
            return ApplyResult.rejected(state, MoveError.NOT_YOUR_PIECE, "Piece belongs to opponent");
        }
        Piece destination = state.board().pieceAt(move.destination()).orElse(null);
        if (destination != null && destination.owner() == source.owner()) {
            return ApplyResult.rejected(
                    state, MoveError.FRIENDLY_DESTINATION, "Cannot capture a friendly piece");
        }

        // TODO(B): validate movement, blocking, check exposure and all endgame rules.
        Board movedBoard = state.board().move(move.source(), move.destination(), source);
        GameState next = new GameState(
                movedBoard,
                state.currentTurn().opposite(),
                destination == null ? state.noCaptureHalfMoves() + 1 : 0,
                0,
                0,
                System.currentTimeMillis(),
                state.status());
        return new ApplyResult(
                next,
                MoveValidationResult.accepted(),
                List.of(new GameEvent.TurnChanged(next.currentTurn())));
    }
}

