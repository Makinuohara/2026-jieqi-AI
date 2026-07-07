package edu.bupt.jieqi.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.bupt.jieqi.model.Board;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.Position;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WireBoardCodec {
    private WireBoardCodec() {
    }

    public static JsonArray encodePieces(Board board) {
        JsonArray pieces = new JsonArray();
        board.publicCopy().pieces().forEach((position, piece) ->
                pieces.add(encodePiece(position, piece)));
        return pieces;
    }

    public static Board decodePieces(JsonArray encodedPieces) {
        Map<Position, Piece> pieces = new LinkedHashMap<>();
        encodedPieces.forEach(element -> {
            String[] parts = element.getAsString().split(":");
            if (parts.length != 5) {
                throw new IllegalArgumentException("Invalid encoded piece: " + element.getAsString());
            }
            Position position = Position.parse(parts[0]);
            Color owner = decodeColor(parts[1]);
            PieceType virtualType = PieceType.fromJsonName(parts[2]);
            boolean visible = "1".equals(parts[4]);
            Piece piece = visible
                    ? Piece.visible(owner, PieceType.fromJsonName(parts[3]))
                    : Piece.hidden(owner, virtualType);
            pieces.put(position, piece);
        });
        return new Board(pieces);
    }

    public static JsonArray encodePublicBoard(Board board) {
        JsonArray cells = new JsonArray();
        board.publicCopy().pieces().forEach((position, piece) -> {
            JsonObject cell = new JsonObject();
            cell.addProperty("x", String.valueOf((char) ('a' + position.file())));
            cell.addProperty("y", position.rank());
            cell.addProperty("piece", piece.visible()
                    ? piece.actualType().jsonName()
                    : piece.virtualType().jsonName());
            cell.addProperty("visible", piece.visible());
            cells.add(cell);
        });
        return cells;
    }

    public static Board decodePublicBoard(JsonArray cells) {
        Map<Position, Piece> pieces = new LinkedHashMap<>();
        cells.forEach(element -> {
            JsonObject cell = element.getAsJsonObject();
            Position position = new Position(
                    cell.get("x").getAsString().charAt(0) - 'a',
                    cell.get("y").getAsInt());
            Color owner = position.rank() <= 4 ? Color.RED : Color.BLACK;
            PieceType type = PieceType.fromJsonName(cell.get("piece").getAsString());
            boolean visible = cell.get("visible").getAsBoolean();
            pieces.put(position, visible ? Piece.visible(owner, type) : Piece.hidden(owner, type));
        });
        return new Board(pieces);
    }

    private static String encodePiece(Position position, Piece piece) {
        String actualType = piece.visible() ? piece.actualType().jsonName() : "-";
        return position + ":"
                + encodeColor(piece.owner()) + ":"
                + piece.virtualType().jsonName() + ":"
                + actualType + ":"
                + (piece.visible() ? "1" : "0");
    }

    private static String encodeColor(Color color) {
        return color == Color.RED ? "R" : "B";
    }

    private static Color decodeColor(String value) {
        return switch (value) {
            case "R" -> Color.RED;
            case "B" -> Color.BLACK;
            default -> throw new IllegalArgumentException("Unknown color: " + value);
        };
    }
}
