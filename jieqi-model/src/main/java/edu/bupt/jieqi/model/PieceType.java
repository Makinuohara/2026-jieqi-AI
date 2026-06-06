package edu.bupt.jieqi.model;

public enum PieceType {
    KING("king", 10_000),
    ROOK("rook", 600),
    KNIGHT("knight", 270),
    CANNON("cannon", 285),
    PAWN("pawn", 30),
    GUARD("guard", 120),
    BISHOP("bishop", 120);

    private final String jsonName;
    private final int baseValue;

    PieceType(String jsonName, int baseValue) {
        this.jsonName = jsonName;
        this.baseValue = baseValue;
    }

    public String jsonName() {
        return jsonName;
    }

    public int baseValue() {
        return baseValue;
    }

    public static PieceType fromJsonName(String value) {
        for (PieceType type : values()) {
            if (type.jsonName.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown piece type: " + value);
    }
}

