package edu.bupt.jieqi.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.bupt.jieqi.model.Board;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.model.Position;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GreedyAgentTest {
    @Test
    void prefersCapturingKingOverMaterialCapture() {
        Move win = move("e8", "e9");
        Move captureRook = move("a0", "a9");
        PlayerView view = view(
                Color.RED,
                pieces(
                        piece("e0", Color.RED, PieceType.KING),
                        piece("e8", Color.RED, PieceType.ROOK),
                        piece("a0", Color.RED, PieceType.ROOK),
                        piece("e9", Color.BLACK, PieceType.KING),
                        piece("a9", Color.BLACK, PieceType.ROOK)),
                win,
                captureRook);

        Move chosen = new GreedyAgent().chooseMove(view, new SearchBudget(Duration.ofSeconds(1), 1));

        assertEquals(win, chosen);
    }

    @Test
    void prefersHigherValueCaptureWhenNoImmediateWinExists() {
        Move captureRook = move("a0", "a9");
        Move capturePawn = move("b0", "b6");
        PlayerView view = view(
                Color.RED,
                pieces(
                        piece("e0", Color.RED, PieceType.KING),
                        piece("a0", Color.RED, PieceType.ROOK),
                        piece("b0", Color.RED, PieceType.ROOK),
                        piece("e5", Color.RED, PieceType.PAWN),
                        piece("e9", Color.BLACK, PieceType.KING),
                        piece("a9", Color.BLACK, PieceType.ROOK),
                        piece("b6", Color.BLACK, PieceType.PAWN)),
                capturePawn,
                captureRook);

        Move chosen = new GreedyAgent().chooseMove(view, new SearchBudget(Duration.ofSeconds(1), 1));

        assertEquals(captureRook, chosen);
    }

    @Test
    void filtersOutGreedyMoveThatLeavesKingCapturable() {
        Move captureRook = move("b9", "b0");
        Move escapeKing = move("e9", "d9");
        PlayerView view = view(
                Color.BLACK,
                pieces(
                        piece("e9", Color.BLACK, PieceType.KING),
                        piece("b9", Color.BLACK, PieceType.ROOK),
                        piece("e0", Color.RED, PieceType.KING),
                        piece("e5", Color.RED, PieceType.ROOK),
                        piece("b0", Color.RED, PieceType.ROOK)),
                captureRook,
                escapeKing);

        Move chosen = new GreedyAgent().chooseMove(view, new SearchBudget(Duration.ofSeconds(1), 1));

        assertEquals(escapeKing, chosen);
    }

    private static PlayerView view(Color turn, Map<Position, Piece> pieces, Move... legalMoves) {
        return new PlayerView(new Board(pieces), turn, turn, List.of(legalMoves));
    }

    @SafeVarargs
    private static Map<Position, Piece> pieces(Map.Entry<Position, Piece>... entries) {
        Map<Position, Piece> pieces = new LinkedHashMap<>();
        for (Map.Entry<Position, Piece> entry : entries) {
            pieces.put(entry.getKey(), entry.getValue());
        }
        return pieces;
    }

    private static Map.Entry<Position, Piece> piece(
            String coordinate, Color color, PieceType type) {
        return Map.entry(pos(coordinate), Piece.visible(color, type));
    }

    private static Position pos(String coordinate) {
        return Position.parse(coordinate);
    }

    private static Move move(String source, String destination) {
        return new Move(pos(source), pos(destination), 0);
    }
}
