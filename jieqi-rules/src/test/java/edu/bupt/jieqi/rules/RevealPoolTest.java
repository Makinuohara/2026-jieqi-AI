package edu.bupt.jieqi.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.bupt.jieqi.model.PieceType;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RevealPoolTest {
    @Test
    void standardPoolContainsFifteenNonKingPieces() {
        RevealPool pool = RevealPool.standard();

        assertEquals(15, pool.remainingCount());
        assertEquals(5, pool.remaining(PieceType.PAWN));
        assertEquals(2, pool.remaining(PieceType.ROOK));
    }

    @Test
    void drawingRemovesOnePiece() {
        RevealPool pool = RevealPool.standard();
        pool.draw(RandomGenerator.getDefault());
        assertEquals(14, pool.remainingCount());
    }
}

