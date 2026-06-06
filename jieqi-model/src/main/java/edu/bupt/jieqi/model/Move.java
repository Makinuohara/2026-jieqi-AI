package edu.bupt.jieqi.model;

import java.util.Objects;

public record Move(Position source, Position destination, long clientTimestamp) {
    public Move {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
    }
}

