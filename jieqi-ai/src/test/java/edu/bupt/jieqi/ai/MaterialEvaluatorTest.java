package edu.bupt.jieqi.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.bupt.jieqi.model.Board;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.HiddenPiecePool;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.model.Position;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaterialEvaluatorTest {
    @Test
    void usesSearchHiddenPoolWhenScoringHiddenPieces() {
        PlayerView view = view(
                Color.RED,
                pieces(
                        piece("e0", Color.RED, PieceType.KING),
                        hidden("a0", Color.RED, PieceType.PAWN),
                        piece("e9", Color.BLACK, PieceType.KING)));
        MaterialEvaluator evaluator = new MaterialEvaluator();

        double mostlyRooks = evaluator.evaluate(
                view, poolWith(PieceType.ROOK, 2), HiddenPiecePool.standard());
        double mostlyPawns = evaluator.evaluate(
                view, poolWith(PieceType.PAWN, 5), HiddenPiecePool.standard());

        assertTrue(mostlyRooks > mostlyPawns + 100.0);
    }

    private static HiddenPiecePool poolWith(PieceType type, int count) {
        EnumMap<PieceType, Integer> counts = new EnumMap<>(PieceType.class);
        counts.put(type, count);
        return new HiddenPiecePool(counts);
    }

    private static PlayerView view(Color turn, Map<Position, Piece> pieces) {
        return new PlayerView(new Board(pieces), turn, turn, List.of());
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
}
