package edu.bupt.jieqi.model;

import java.util.Objects;
import java.util.Optional;

public record Piece(Color owner, PieceType virtualType, PieceType actualType, boolean visible) {
    public Piece {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(virtualType, "virtualType");
        if (visible && actualType == null) {
            throw new IllegalArgumentException("A visible piece must have an actual type");
        }
    }

    public static Piece hidden(Color owner, PieceType virtualType) {
        return new Piece(owner, virtualType, null, false);
    }

    public static Piece visible(Color owner, PieceType type) {
        return new Piece(owner, type, type, true);
    }

    public Optional<PieceType> knownActualType() {
        return Optional.ofNullable(actualType);
    }

    public Piece revealAs(PieceType type) {
        return new Piece(owner, virtualType, Objects.requireNonNull(type), true);
    }

    public PieceType movementType() {
        return visible ? actualType : virtualType;
    }

    public Piece publicCopy() {
        return visible ? this : hidden(owner, virtualType);
    }
}

