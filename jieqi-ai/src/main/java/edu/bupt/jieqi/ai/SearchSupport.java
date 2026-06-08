package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.Board;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.HiddenPiecePool;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.rules.GameEngine;
import java.util.EnumMap;
import java.util.Map;
import java.util.random.RandomGenerator;

final class SearchSupport {
    static final double WIN_SCORE = 1_000_000.0;

    private SearchSupport() {
    }

    static GameState stateFrom(PlayerView view) {
        return new GameState(
                view.board(),
                view.currentTurn(),
                0,
                0, 0, null, null,
                0, 0, null, null,
                0,
                GameStatus.PLAYING,
                inferHiddenPool(view.board(), Color.RED),
                inferHiddenPool(view.board(), Color.BLACK));
    }

    static PlayerView viewFrom(GameState state, Color perspective, GameEngine engine) {
        return PlayerView.from(state, perspective, engine.legalMoves(state));
    }

    static HiddenPiecePool inferHiddenPool(Board board, Color owner) {
        EnumMap<PieceType, Integer> counts = new EnumMap<>(HiddenPiecePool.standard().counts());
        for (Piece piece : board.pieces().values()) {
            if (piece.owner() == owner && piece.visible() && piece.actualType() != PieceType.KING) {
                counts.computeIfPresent(piece.actualType(), (ignored, count) -> Math.max(0, count - 1));
            }
        }
        return new HiddenPiecePool(counts);
    }

    static double expectedHiddenValue(HiddenPiecePool pool) {
        int total = pool.total();
        if (total == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (Map.Entry<PieceType, Integer> entry : pool.counts().entrySet()) {
            sum += entry.getKey().baseValue() * entry.getValue();
        }
        return sum / total;
    }

    static double terminalValue(GameStatus status, Color perspective) {
        return switch (status) {
            case RED_WIN -> perspective == Color.RED ? WIN_SCORE : -WIN_SCORE;
            case BLACK_WIN -> perspective == Color.BLACK ? WIN_SCORE : -WIN_SCORE;
            case DRAW -> 0.0;
            case WAITING, PLAYING -> Double.NaN;
        };
    }

    static int firstDrawIndex(HiddenPiecePool pool, PieceType desiredType) {
        int index = 0;
        for (PieceType type : PieceType.values()) {
            int count = pool.count(type);
            if (type == desiredType) {
                if (count <= 0) {
                    throw new IllegalArgumentException("No hidden piece of type " + desiredType + " remains");
                }
                return index;
            }
            index += count;
        }
        throw new IllegalArgumentException("No hidden piece of type " + desiredType + " remains");
    }

    static final class FixedDrawRandom implements RandomGenerator {
        private final int selectedIndex;

        FixedDrawRandom(int selectedIndex) {
            this.selectedIndex = selectedIndex;
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            return Math.min(selectedIndex, bound - 1);
        }

        @Override
        public long nextLong() {
            return selectedIndex;
        }
    }
}
