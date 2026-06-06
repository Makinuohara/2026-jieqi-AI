package edu.bupt.jieqi.protocol;

public final class Messages {
    private Messages() {
    }

    public record Login(String messageType, String userId, String password) {
        public Login(String userId, String password) {
            this("Login", userId, password);
        }
    }

    public record MoveRequest(
            String messageType,
            String fromX,
            int fromY,
            String toX,
            int toY,
            boolean isFlip) {
        public MoveRequest(String fromX, int fromY, String toX, int toY, boolean isFlip) {
            this("move", fromX, fromY, toX, toY, isFlip);
        }
    }

    public record Pong(String messageType, long timestamp) {
        public Pong(long timestamp) {
            this("pong", timestamp);
        }
    }

    public record Error(String messageType, int code, String message) {
        public Error(int code, String message) {
            this("error", code, message);
        }
    }

    public record MoveResult(
            String messageType,
            boolean success,
            boolean valid,
            MoveRequest move,
            String flipResult,
            String capturedType) {
        public MoveResult(
                boolean success,
                boolean valid,
                MoveRequest move,
                String flipResult,
                String capturedType) {
            this("moveResult", success, valid, move, flipResult, capturedType);
        }
    }
}

