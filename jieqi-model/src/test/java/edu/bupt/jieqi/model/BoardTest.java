package edu.bupt.jieqi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoardTest {
    @Test
    void initialBoardHasVisibleKingsAndThirtyHiddenPieces() {
        Board board = Board.initial();

        assertEquals(32, board.pieces().size());
        assertTrue(board.pieceAt(Position.parse("e0")).orElseThrow().visible());
        assertTrue(board.pieceAt(Position.parse("e9")).orElseThrow().visible());
        assertEquals(30, board.pieces().values().stream().filter(piece -> !piece.visible()).count());
        assertFalse(board.pieceAt(Position.parse("a0")).orElseThrow().visible());
    }

    @Test
    void movingPieceCanRevealItAtDestination() {
        Board board = Board.initial();
        Position source = Position.parse("a0");
        Position destination = Position.parse("a1");
        Piece revealed = board.pieceAt(source).orElseThrow().revealAs(PieceType.CANNON);

        Board moved = board.move(source, destination, revealed);

        assertTrue(moved.pieceAt(source).isEmpty());
        assertEquals(PieceType.CANNON,
                moved.pieceAt(destination).orElseThrow().knownActualType().orElseThrow());
    }
}
