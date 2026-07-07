package edu.bupt.jieqi.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import edu.bupt.jieqi.model.Board;
import org.junit.jupiter.api.Test;

class WireBoardCodecTest {
    @Test
    void encodedBoardKeepsHiddenActualTypesPrivate() {
        JsonArray pieces = WireBoardCodec.encodePieces(Board.initial());
        String text = pieces.toString();

        assertTrue(text.contains(":king:king:1"));
        assertFalse(text.contains(":rook:rook:0"));
        assertFalse(text.contains(":pawn:pawn:0"));
    }

    @Test
    void decodesPublicBoardBackToSamePieceCount() {
        Board decoded = WireBoardCodec.decodePieces(WireBoardCodec.encodePieces(Board.initial()));

        assertTrue(decoded.pieces().size() == Board.initial().pieces().size());
    }

    @Test
    void encodesPublicInterfaceInitialBoardCells() {
        JsonArray cells = WireBoardCodec.encodePublicBoard(Board.initial());
        String text = cells.toString();

        assertTrue(text.contains("\"x\":\"a\""));
        assertTrue(text.contains("\"y\":0"));
        assertTrue(text.contains("\"piece\":\"rook\""));
        assertTrue(text.contains("\"visible\":false"));
    }
}
