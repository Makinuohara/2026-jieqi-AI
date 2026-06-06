package edu.bupt.jieqi.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class Board {
    private final Map<Position, Piece> pieces;

    public Board(Map<Position, Piece> pieces) {
        this.pieces = Collections.unmodifiableMap(new LinkedHashMap<>(pieces));
    }

    public static Board initial() {
        Map<Position, Piece> pieces = new LinkedHashMap<>();
        addBackRank(pieces, Color.RED, 0);
        addCannons(pieces, Color.RED, 2);
        addPawns(pieces, Color.RED, 3);
        addBackRank(pieces, Color.BLACK, 9);
        addCannons(pieces, Color.BLACK, 7);
        addPawns(pieces, Color.BLACK, 6);
        return new Board(pieces);
    }

    private static void addBackRank(Map<Position, Piece> pieces, Color color, int rank) {
        PieceType[] types = {
            PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.GUARD,
            PieceType.KING, PieceType.GUARD, PieceType.BISHOP, PieceType.KNIGHT,
            PieceType.ROOK
        };
        for (int file = 0; file < types.length; file++) {
            PieceType type = types[file];
            pieces.put(new Position(file, rank),
                    type == PieceType.KING ? Piece.visible(color, type) : Piece.hidden(color, type));
        }
    }

    private static void addCannons(Map<Position, Piece> pieces, Color color, int rank) {
        pieces.put(new Position(1, rank), Piece.hidden(color, PieceType.CANNON));
        pieces.put(new Position(7, rank), Piece.hidden(color, PieceType.CANNON));
    }

    private static void addPawns(Map<Position, Piece> pieces, Color color, int rank) {
        for (int file = 0; file <= 8; file += 2) {
            pieces.put(new Position(file, rank), Piece.hidden(color, PieceType.PAWN));
        }
    }

    public Map<Position, Piece> pieces() {
        return pieces;
    }

    public Optional<Piece> pieceAt(Position position) {
        return Optional.ofNullable(pieces.get(position));
    }

    public Board move(Position source, Position destination, Piece movedPiece) {
        Objects.requireNonNull(movedPiece, "movedPiece");
        Map<Position, Piece> copy = new LinkedHashMap<>(pieces);
        copy.remove(source);
        copy.put(destination, movedPiece);
        return new Board(copy);
    }

    public Board publicCopy() {
        Map<Position, Piece> copy = new LinkedHashMap<>();
        pieces.forEach((position, piece) -> copy.put(position, piece.publicCopy()));
        return new Board(copy);
    }
}

