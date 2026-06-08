package edu.bupt.jieqi.ai;

import edu.bupt.jieqi.model.Board;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.HiddenPiecePool;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.model.Position;
import edu.bupt.jieqi.rules.GameEngine;
import edu.bupt.jieqi.rules.ApplyResult;
import edu.bupt.jieqi.rules.StandardGameEngine;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

final class SearchSupport {
    static final double WIN_SCORE = 1_000_000.0;
    private static final GameEngine DEFAULT_ENGINE = new StandardGameEngine();

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

    static GameState stateFrom(PlayerView view, HiddenPiecePool redPool, HiddenPiecePool blackPool) {
        return new GameState(
                view.board(),
                view.currentTurn(),
                0,
                0, 0, null, null,
                0, 0, null, null,
                0,
                GameStatus.PLAYING,
                redPool,
                blackPool);
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

    static HiddenPiecePool inferHiddenPool(
            Board board,
            Color owner,
            Map<PieceType, Integer> knownRevealedCounts) {
        EnumMap<PieceType, Integer> counts = new EnumMap<>(HiddenPiecePool.standard().counts());
        for (Piece piece : board.pieces().values()) {
            if (piece.owner() == owner && piece.visible() && piece.actualType() != PieceType.KING) {
                counts.computeIfPresent(piece.actualType(), (ignored, count) -> Math.max(0, count - 1));
            }
        }
        for (Map.Entry<PieceType, Integer> entry : knownRevealedCounts.entrySet()) {
            if (entry.getKey() == PieceType.KING) {
                continue;
            }
            int remembered = Math.max(0, entry.getValue());
            int visible = visibleCount(board, owner, entry.getKey());
            int capturedOrMovedOutOfSight = Math.max(0, remembered - visible);
            counts.computeIfPresent(entry.getKey(),
                    (ignored, count) -> Math.max(0, count - capturedOrMovedOutOfSight));
        }
        return new HiddenPiecePool(counts);
    }

    private static int visibleCount(Board board, Color owner, PieceType type) {
        int count = 0;
        for (Piece piece : board.pieces().values()) {
            if (piece.owner() == owner
                    && piece.visible()
                    && piece.actualType() == type) {
                count++;
            }
        }
        return count;
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

    static Position visibleKingPosition(Board board, Color owner) {
        return board.pieces().entrySet().stream()
                .filter(entry -> entry.getValue().owner() == owner)
                .filter(entry -> entry.getValue().visible())
                .filter(entry -> entry.getValue().actualType() == PieceType.KING)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    static boolean canCaptureVisibleKing(PlayerView view, Color attacker, Color kingOwner) {
        return hasImmediateKingCapture(stateFrom(view), attacker, kingOwner, DEFAULT_ENGINE);
    }

    static boolean hasImmediateKingCapture(
            GameState state,
            Color attacker,
            Color kingOwner,
            GameEngine engine) {
        Position king = visibleKingPosition(state.board(), kingOwner);
        if (king == null || state.status() != GameStatus.PLAYING) {
            return false;
        }
        return engine.legalMoves(withTurn(state, attacker)).stream()
                .anyMatch(move -> move.destination().equals(king));
    }

    static List<Move> safetyFilteredMoves(
            GameState state,
            List<Move> legalMoves,
            Color mover,
            GameEngine engine) {
        List<Move> safeMoves = safeMoves(state, legalMoves, mover, engine);
        return safeMoves.isEmpty() ? legalMoves : safeMoves;
    }

    static List<Move> safeMoves(
            GameState state,
            List<Move> legalMoves,
            Color mover,
            GameEngine engine) {
        return legalMoves.stream()
                .filter(move -> isKingSafeAfterMove(state, move, mover, engine))
                .collect(Collectors.toList());
    }

    static boolean isKingSafeAfterMove(
            GameState state,
            Move move,
            Color kingOwner,
            GameEngine engine) {
        List<WeightedState> outcomes = outcomesAfter(state, move, engine);
        if (outcomes.isEmpty()) {
            return false;
        }
        for (WeightedState outcome : outcomes) {
            GameState next = outcome.state();
            if (next.status() != GameStatus.PLAYING) {
                continue;
            }
            if (hasImmediateKingCapture(next, kingOwner.opposite(), kingOwner, engine)) {
                return false;
            }
        }
        return true;
    }

    static List<WeightedState> outcomesAfter(GameState state, Move move, GameEngine engine) {
        Piece source = state.board().pieceAt(move.source()).orElse(null);
        if (source == null) {
            return List.of();
        }
        if (source.visible()) {
            return singleOutcome(engine, state, move);
        }

        HiddenPiecePool pool = state.hiddenPool(source.owner());
        if (pool.total() == 0) {
            return singleOutcome(engine, state, move);
        }

        java.util.ArrayList<WeightedState> outcomes = new java.util.ArrayList<>();
        for (PieceType type : PieceType.values()) {
            int count = pool.count(type);
            if (count == 0) {
                continue;
            }
            StandardGameEngine fixedEngine = new StandardGameEngine(
                    new FixedDrawRandom(firstDrawIndex(pool, type)));
            ApplyResult result = fixedEngine.apply(state, move);
            if (result.validation().valid()) {
                outcomes.add(new WeightedState(result.state(), (double) count / pool.total()));
            }
        }
        return List.copyOf(outcomes);
    }

    private static List<WeightedState> singleOutcome(
            GameEngine engine,
            GameState state,
            Move move) {
        ApplyResult result = engine.apply(state, move);
        if (!result.validation().valid()) {
            return List.of();
        }
        return List.of(new WeightedState(result.state(), 1.0));
    }

    static double immediateCaptureValue(PlayerView view, Color attacker) {
        GameState state = withTurn(stateFrom(view), attacker);
        List<Move> legalMoves = DEFAULT_ENGINE.legalMoves(state);
        double best = 0.0;
        for (Move move : legalMoves) {
            Piece target = view.board().pieceAt(move.destination()).orElse(null);
            if (target == null || target.owner() == attacker) {
                continue;
            }
            double value = target.visible()
                    ? target.actualType().baseValue()
                    : expectedHiddenValue(inferHiddenPool(view.board(), target.owner()));
            best = Math.max(best, value);
        }
        return best;
    }

    static GameState withTurn(GameState state, Color currentTurn) {
        return new GameState(
                state.board(),
                currentTurn,
                state.noCaptureHalfMoves(),
                state.redConsecutiveCheckCount(),
                state.redConsecutiveChaseCount(),
                state.redChasedPosition(),
                state.redChasedPieceType(),
                state.blackConsecutiveCheckCount(),
                state.blackConsecutiveChaseCount(),
                state.blackChasedPosition(),
                state.blackChasedPieceType(),
                state.turnStartedAt(),
                state.status(),
                state.redHiddenPool(),
                state.blackHiddenPool());
    }

    static String transpositionKey(GameState state, Color perspective) {
        StringBuilder key = new StringBuilder(256);
        key.append("p=").append(perspective)
                .append(";t=").append(state.currentTurn())
                .append(";s=").append(state.status())
                .append(";n=").append(state.noCaptureHalfMoves())
                .append(";rc=").append(state.redConsecutiveCheckCount())
                .append(",").append(state.redConsecutiveChaseCount())
                .append(";bc=").append(state.blackConsecutiveCheckCount())
                .append(",").append(state.blackConsecutiveChaseCount())
                .append(";rp=").append(poolKey(state.redHiddenPool()))
                .append(";bp=").append(poolKey(state.blackHiddenPool()))
                .append(";b=");
        for (int rank = 0; rank <= 9; rank++) {
            for (int file = 0; file <= 8; file++) {
                Position position = new Position(file, rank);
                Piece piece = state.board().pieceAt(position).orElse(null);
                if (piece == null) {
                    key.append('.');
                    continue;
                }
                key.append(piece.owner() == Color.RED ? 'r' : 'b')
                        .append(piece.virtualType().ordinal())
                        .append(piece.visible() ? 'v' : 'h');
                if (piece.visible()) {
                    key.append(piece.actualType().ordinal());
                }
                key.append('@');
            }
        }
        return key.toString();
    }

    private static String poolKey(HiddenPiecePool pool) {
        StringBuilder key = new StringBuilder();
        for (PieceType type : PieceType.values()) {
            key.append(type.ordinal()).append(':').append(pool.count(type)).append(',');
        }
        return key.toString();
    }

    static record WeightedState(GameState state, double probability) {
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
