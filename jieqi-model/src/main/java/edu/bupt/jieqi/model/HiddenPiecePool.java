package edu.bupt.jieqi.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class HiddenPiecePool {
    private final Map<PieceType, Integer> counts;

    public HiddenPiecePool(Map<PieceType, Integer> counts) {
        Objects.requireNonNull(counts, "counts");
        EnumMap<PieceType, Integer> copy = new EnumMap<>(PieceType.class);
        for (PieceType type : PieceType.values()) {
            int count = counts.getOrDefault(type, 0);
            if (count < 0) {
                throw new IllegalArgumentException("Piece count cannot be negative");
            }
            copy.put(type, count);
        }
        this.counts = Map.copyOf(copy);
    }

    public static HiddenPiecePool standard() {
        EnumMap<PieceType, Integer> counts = new EnumMap<>(PieceType.class);
        counts.put(PieceType.ROOK, 2);
        counts.put(PieceType.KNIGHT, 2);
        counts.put(PieceType.CANNON, 2);
        counts.put(PieceType.PAWN, 5);
        counts.put(PieceType.GUARD, 2);
        counts.put(PieceType.BISHOP, 2);
        return new HiddenPiecePool(counts);
    }

    public int count(PieceType type) {
        return counts.getOrDefault(Objects.requireNonNull(type), 0);
    }

    public int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<PieceType, Integer> counts() {
        return counts;
    }

    public HiddenPiecePool remove(PieceType type) {
        Objects.requireNonNull(type, "type");
        int current = count(type);
        if (current == 0) {
            throw new IllegalArgumentException("No hidden piece of type " + type + " remains");
        }
        EnumMap<PieceType, Integer> updated = new EnumMap<>(counts);
        updated.put(type, current - 1);
        return new HiddenPiecePool(updated);
    }
}
