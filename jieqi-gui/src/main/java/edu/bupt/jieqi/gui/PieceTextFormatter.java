package edu.bupt.jieqi.gui;

import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;

final class PieceTextFormatter {
    private PieceTextFormatter() {
    }

    static String format(Piece piece) {
        if (piece == null) {
            return "";
        }
        if (!piece.visible()) {
            return "暗";
        }

        PieceType type = piece.actualType();
        return switch (type) {
            case KING -> piece.owner() == Color.RED ? "帅" : "将";
            case GUARD -> piece.owner() == Color.RED ? "仕" : "士";
            case BISHOP -> piece.owner() == Color.RED ? "相" : "象";
            case PAWN -> piece.owner() == Color.RED ? "兵" : "卒";
            case ROOK -> "车";
            case KNIGHT -> "马";
            case CANNON -> "炮";
        };
    }
}
