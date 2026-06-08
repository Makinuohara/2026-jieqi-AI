package edu.bupt.jieqi.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

class ExpectiminimaxAgentTest {
    @Test
    void searchesOpponentReplyBeforeChoosingMove() {
        Move exposedMove = move("a0", "a1");
        Move safeMove = move("a0", "b0");
        PlayerView view = view(
                Color.RED,
                pieces(
                        piece("e0", Color.RED, PieceType.KING),
                        piece("a0", Color.RED, PieceType.ROOK),
                        piece("e5", Color.RED, PieceType.PAWN),
                        piece("e9", Color.BLACK, PieceType.KING),
                        piece("c1", Color.BLACK, PieceType.ROOK)),
                exposedMove,
                safeMove);

        Evaluator redRookSurvival = currentView -> currentView.board().pieces().values().stream()
                .anyMatch(piece -> piece.owner() == Color.RED
                        && piece.visible()
                        && piece.actualType() == PieceType.ROOK)
                ? 100.0
                : -100.0;
        Move chosen = new ExpectiminimaxAgent(redRookSurvival)
                .chooseMove(view, new SearchBudget(Duration.ofSeconds(1), 2));

        assertEquals(safeMove, chosen);
    }

    @Test
    void expandsHiddenRevealChanceNodes() {
        Move reveal = move("a0", "a1");
        Move capturePawn = move("b0", "b6");
        PlayerView view = view(
                Color.RED,
                pieces(
                        piece("e0", Color.RED, PieceType.KING),
                        hidden("a0", Color.RED, PieceType.ROOK),
                        piece("b0", Color.RED, PieceType.ROOK),
                        piece("e5", Color.RED, PieceType.PAWN),
                        piece("e9", Color.BLACK, PieceType.KING),
                        piece("b6", Color.BLACK, PieceType.PAWN)),
                capturePawn,
                reveal);

        Evaluator revealValue = currentView -> currentView.board()
                .pieceAt(pos("a1"))
                .filter(Piece::visible)
                .map(piece -> (double) piece.actualType().baseValue())
                .orElse(100.0);
        Move chosen = new ExpectiminimaxAgent(revealValue)
                .chooseMove(view, new SearchBudget(Duration.ofSeconds(1), 1));

        assertEquals(reveal, chosen);
    }

    @Test
    void avoidsMoveThatAllowsImmediateKingCapture() {
        Move irrelevantMove = move("b0", "b1");
        Move captureCheckingRook = move("a5", "e5");
        PlayerView view = view(
                Color.BLACK,
                pieces(
                        piece("e9", Color.BLACK, PieceType.KING),
                        piece("a5", Color.BLACK, PieceType.ROOK),
                        piece("b0", Color.BLACK, PieceType.ROOK),
                        piece("e0", Color.RED, PieceType.KING),
                        piece("e5", Color.RED, PieceType.ROOK)),
                irrelevantMove,
                captureCheckingRook);

        Move chosen = new ExpectiminimaxAgent(new MaterialEvaluator())
                .chooseMove(view, new SearchBudget(Duration.ofSeconds(1), 2));

        assertEquals(captureCheckingRook, chosen);
    }

    @Test
    void mustAnswerCheckBeforePlayingElsewhere() {
        Move escapeKing = move("e9", "d9");
        Move blockWithRook = move("a7", "e7");
        Move captureMaterial = move("b9", "b0");
        PlayerView view = view(
                Color.BLACK,
                pieces(
                        piece("e9", Color.BLACK, PieceType.KING),
                        piece("a7", Color.BLACK, PieceType.ROOK),
                        piece("b9", Color.BLACK, PieceType.ROOK),
                        piece("e0", Color.RED, PieceType.KING),
                        piece("e5", Color.RED, PieceType.ROOK),
                        piece("b0", Color.RED, PieceType.ROOK)),
                captureMaterial,
                escapeKing,
                blockWithRook);

        Move chosen = new ExpectiminimaxAgent(new MaterialEvaluator())
                .chooseMove(view, new SearchBudget(Duration.ofSeconds(1), 2));

        assertNotEquals(captureMaterial, chosen);
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

    private static Map.Entry<Position, Piece> hidden(
            String coordinate, Color color, PieceType virtualType) {
        return Map.entry(pos(coordinate), Piece.hidden(color, virtualType));
    }

    private static Position pos(String coordinate) {
        return Position.parse(coordinate);
    }

    private static Move move(String source, String destination) {
        return new Move(pos(source), pos(destination), 0);
    }
}
