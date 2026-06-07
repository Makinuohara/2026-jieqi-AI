package edu.bupt.jieqi.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import org.junit.jupiter.api.Test;

class PieceTextFormatterTest {
    @Test
    void displaysHiddenAndEmptySquares() {
        assertEquals("", PieceTextFormatter.format(null));
        assertEquals("暗", PieceTextFormatter.format(Piece.hidden(Color.RED, PieceType.ROOK)));
    }

    @Test
    void displaysRedPieceNames() {
        assertEquals("帅", visibleName(Color.RED, PieceType.KING));
        assertEquals("仕", visibleName(Color.RED, PieceType.GUARD));
        assertEquals("相", visibleName(Color.RED, PieceType.BISHOP));
        assertEquals("兵", visibleName(Color.RED, PieceType.PAWN));
        assertEquals("车", visibleName(Color.RED, PieceType.ROOK));
        assertEquals("马", visibleName(Color.RED, PieceType.KNIGHT));
        assertEquals("炮", visibleName(Color.RED, PieceType.CANNON));
    }

    @Test
    void displaysBlackPieceNames() {
        assertEquals("将", visibleName(Color.BLACK, PieceType.KING));
        assertEquals("士", visibleName(Color.BLACK, PieceType.GUARD));
        assertEquals("象", visibleName(Color.BLACK, PieceType.BISHOP));
        assertEquals("卒", visibleName(Color.BLACK, PieceType.PAWN));
        assertEquals("车", visibleName(Color.BLACK, PieceType.ROOK));
        assertEquals("马", visibleName(Color.BLACK, PieceType.KNIGHT));
        assertEquals("炮", visibleName(Color.BLACK, PieceType.CANNON));
    }

    @Test
    void displaysRevealedTypeInsteadOfVirtualType() {
        Piece revealed = Piece.hidden(Color.RED, PieceType.PAWN).revealAs(PieceType.GUARD);

        assertEquals("仕", PieceTextFormatter.format(revealed));
    }

    @Test
    void moveRecordNamesAlsoDistinguishBothSides() {
        assertEquals("帅", LocalHumanVsAiGame.pieceName(PieceType.KING, Color.RED));
        assertEquals("将", LocalHumanVsAiGame.pieceName(PieceType.KING, Color.BLACK));
        assertEquals("兵", LocalHumanVsAiGame.pieceName(PieceType.PAWN, Color.RED));
        assertEquals("卒", LocalHumanVsAiGame.pieceName(PieceType.PAWN, Color.BLACK));
    }

    private String visibleName(Color color, PieceType type) {
        return PieceTextFormatter.format(Piece.visible(color, type));
    }
}
