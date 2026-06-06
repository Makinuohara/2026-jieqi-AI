package edu.bupt.jieqi.rules;

import edu.bupt.jieqi.model.PieceType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.random.RandomGenerator;

public final class RevealPool {
    private final List<PieceType> remaining;

    private RevealPool(List<PieceType> remaining) {
        this.remaining = new ArrayList<>(remaining);
    }

    public static RevealPool standard() {
        List<PieceType> pieces = new ArrayList<>();
        add(pieces, PieceType.ROOK, 2);
        add(pieces, PieceType.KNIGHT, 2);
        add(pieces, PieceType.CANNON, 2);
        add(pieces, PieceType.PAWN, 5);
        add(pieces, PieceType.GUARD, 2);
        add(pieces, PieceType.BISHOP, 2);
        return new RevealPool(pieces);
    }

    private static void add(List<PieceType> pieces, PieceType type, int count) {
        for (int i = 0; i < count; i++) {
            pieces.add(type);
        }
    }

    public PieceType draw(RandomGenerator random) {
        if (remaining.isEmpty()) {
            throw new NoSuchElementException("No hidden pieces remain");
        }
        return remaining.remove(random.nextInt(remaining.size()));
    }

    public int remainingCount() {
        return remaining.size();
    }

    public int remaining(PieceType type) {
        return (int) remaining.stream().filter(type::equals).count();
    }

    public Map<PieceType, Integer> snapshot() {
        Map<PieceType, Integer> counts = new EnumMap<>(PieceType.class);
        for (PieceType type : PieceType.values()) {
            counts.put(type, remaining(type));
        }
        return Map.copyOf(counts);
    }
}

