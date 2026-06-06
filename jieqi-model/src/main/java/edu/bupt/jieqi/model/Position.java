package edu.bupt.jieqi.model;

public record Position(int file, int rank) {
    public Position {
        if (file < 0 || file > 8 || rank < 0 || rank > 9) {
            throw new IllegalArgumentException("Position must be within files a-i and ranks 0-9");
        }
    }

    public static Position parse(String coordinate) {
        if (coordinate == null || coordinate.length() != 2) {
            throw new IllegalArgumentException("Coordinate must look like a0");
        }
        return new Position(coordinate.charAt(0) - 'a', coordinate.charAt(1) - '0');
    }

    @Override
    public String toString() {
        return String.valueOf((char) ('a' + file)) + rank;
    }
}

