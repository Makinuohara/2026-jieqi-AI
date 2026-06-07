package edu.bupt.jieqi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HiddenPiecePoolTest {
    @Test
    void standardPoolContainsFifteenNonKingPieces() {
        HiddenPiecePool pool = HiddenPiecePool.standard();

        assertEquals(15, pool.total());
        assertEquals(5, pool.count(PieceType.PAWN));
        assertEquals(2, pool.count(PieceType.ROOK));
        assertEquals(0, pool.count(PieceType.KING));
    }

    @Test
    void removingPieceReturnsNewPoolWithoutChangingOriginal() {
        HiddenPiecePool original = HiddenPiecePool.standard();

        HiddenPiecePool updated = original.remove(PieceType.ROOK);

        assertEquals(2, original.count(PieceType.ROOK));
        assertEquals(1, updated.count(PieceType.ROOK));
        assertEquals(14, updated.total());
    }

    @Test
    void cannotRemoveUnavailablePiece() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HiddenPiecePool.standard().remove(PieceType.KING));
    }
}
