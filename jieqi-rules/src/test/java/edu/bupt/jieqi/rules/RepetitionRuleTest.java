package edu.bupt.jieqi.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.bupt.jieqi.model.Board;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.HiddenPiecePool;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.Position;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/**
 * Tests for perpetual check (长将) and perpetual chase (长捉) rules,
 * including pawn exceptions and per-side counter isolation.
 */
class RepetitionRuleTest {

    private final GameEngine engine = new StandardGameEngine(new FirstChoiceRandom());

    // ─── Perpetual Check (长将) ───────────────────────────────────────

    @Test
    void checkCounterIncrementsOnConsecutiveChecks() {
        // Red rook oscillates e4↔e5 — both check Black king at e9.
        // Black rook at d9 provides a safe non-check move.
        GameState state = initialState(Color.RED,
                piece("e4", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("e9", Color.BLACK, PieceType.KING),
                piece("d9", Color.BLACK, PieceType.ROOK));

        // Red 1: e4→e5 (check)
        state = apply(state, move("e4", "e5"));
        assertEquals(1, state.consecutiveCheckCount(Color.RED));
        assertEquals(Color.BLACK, state.currentTurn());

        // Black: d9→d8 (non-check)
        state = apply(state, move("d9", "d8"));
        assertEquals(0, state.consecutiveCheckCount(Color.BLACK));
        assertEquals(1, state.consecutiveCheckCount(Color.RED)); // preserved
        assertEquals(Color.RED, state.currentTurn());

        // Red 2: e5→e4 (check)
        state = apply(state, move("e5", "e4"));
        assertEquals(2, state.consecutiveCheckCount(Color.RED));

        // Black: d8→d7
        state = apply(state, move("d8", "d7"));

        // Red 3: e4→e5 (check)
        state = apply(state, move("e4", "e5"));
        assertEquals(3, state.consecutiveCheckCount(Color.RED));
    }

    @Test
    void checkCounterResetsWhenNoCheck() {
        GameState state = initialState(Color.RED,
                piece("e4", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("e9", Color.BLACK, PieceType.KING),
                piece("d9", Color.BLACK, PieceType.ROOK));

        // Red: check
        state = apply(state, move("e4", "e5"));
        assertEquals(1, state.consecutiveCheckCount(Color.RED));

        // Black: non-check
        state = apply(state, move("d9", "d8"));

        // Red: move to f5 — NOT a check (off the e-file)
        state = apply(state, move("e5", "f5"));
        assertEquals(0, state.consecutiveCheckCount(Color.RED));
    }

    @Test
    void sixConsecutiveChecksByRedTriggersBlackWin() {
        // Red rook alternates e4↔e5 (both check Black king at e9).
        // After 6 Red checks, Black wins.
        GameState state = initialState(Color.RED,
                piece("e4", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("e9", Color.BLACK, PieceType.KING),
                piece("d9", Color.BLACK, PieceType.ROOK));

        for (int i = 0; i < 6; i++) {
            // Red: check (alternate e4↔e5)
            String from = (i % 2 == 0) ? "e4" : "e5";
            String to = (i % 2 == 0) ? "e5" : "e4";
            state = apply(state, move(from, to));
            if (i < 5) {
                assertEquals(GameStatus.PLAYING, state.status(),
                        "Should still be playing after check #" + (i + 1));
            }
            if (state.status() != GameStatus.PLAYING) break;

            // Black: move rook away and back (d-file, clear of e-file)
            String bFrom = (i % 2 == 0) ? "d9" : "d8";
            String bTo = (i % 2 == 0) ? "d8" : "d9";
            state = apply(state, move(bFrom, bTo));
        }

        assertEquals(GameStatus.BLACK_WIN, state.status(),
                "Red's 6th consecutive check → Black wins");
    }

    @Test
    void blackCheckCounterIndependentOfRed() {
        GameState state = initialState(Color.RED,
                piece("e4", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d9", Color.BLACK, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING));

        // Red: check
        state = apply(state, move("e4", "e5"));
        assertEquals(1, state.consecutiveCheckCount(Color.RED));
        assertEquals(0, state.consecutiveCheckCount(Color.BLACK));

        // Black: non-check (d9→d8)
        state = apply(state, move("d9", "d8"));
        assertEquals(1, state.consecutiveCheckCount(Color.RED)); // preserved
        assertEquals(0, state.consecutiveCheckCount(Color.BLACK)); // no check

        // Red: check again
        state = apply(state, move("e5", "e4"));
        assertEquals(2, state.consecutiveCheckCount(Color.RED));
    }

    // ─── Perpetual Chase (长捉) ──────────────────────────────────────

    @Test
    void chaseDetectedWhenMovedPieceCreatesNewAttack() {
        // Red rook at e6 → d6: now attacks Black's visible rook at d7.
        GameState state = initialState(Color.RED,
                piece("e6", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d7", Color.BLACK, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING));

        state = apply(state, move("e6", "d6"));

        assertEquals(1, state.consecutiveChaseCount(Color.RED));
        assertNotNull(state.chasedPosition(Color.RED));
        assertEquals(pos("d7"), state.chasedPosition(Color.RED));
    }

    @Test
    void chaseCounterResetsWhenNoNewAttack() {
        GameState state = initialState(Color.RED,
                piece("e6", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d7", Color.BLACK, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING),
                piece("d9", Color.BLACK, PieceType.ROOK));

        // Red: chase d7
        state = apply(state, move("e6", "d6"));
        assertEquals(1, state.consecutiveChaseCount(Color.RED));

        // Black: move safe rook
        state = apply(state, move("d9", "d8"));

        // Red: move to e6 — no longer attacks d7 → no chase
        state = apply(state, move("d6", "e6"));
        assertEquals(0, state.consecutiveChaseCount(Color.RED));
        assertNull(state.chasedPosition(Color.RED));
    }

    @Test
    void chaseCounterResetsOnDifferentTarget() {
        GameState state = initialState(Color.RED,
                piece("f6", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d7", Color.BLACK, PieceType.ROOK),
                piece("g7", Color.BLACK, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING),
                piece("d9", Color.BLACK, PieceType.ROOK));

        // Red: f6→d6 → attacks d7 → chase #1 on d7
        state = apply(state, move("f6", "d6"));
        assertEquals(1, state.consecutiveChaseCount(Color.RED));
        assertEquals(pos("d7"), state.chasedPosition(Color.RED));

        // Black: move safe rook
        state = apply(state, move("d9", "d8"));

        // Red: d6→g6 → attacks g7 (different target) → reset to 1
        state = apply(state, move("d6", "g6"));
        assertEquals(1, state.consecutiveChaseCount(Color.RED));
        assertEquals(pos("g7"), state.chasedPosition(Color.RED));
    }

    @Test
    void chaseDoesNotCountHiddenPiece() {
        GameState state = initialState(Color.RED,
                piece("e6", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                Map.entry(pos("d7"), Piece.hidden(Color.BLACK, PieceType.ROOK)),
                piece("e9", Color.BLACK, PieceType.KING));

        state = apply(state, move("e6", "d6"));

        // Hidden pieces should not be chase targets
        assertEquals(0, state.consecutiveChaseCount(Color.RED));
        assertNull(state.chasedPosition(Color.RED));
    }

    @Test
    void chaseDoesNotCountKing() {
        GameState state = initialState(Color.RED,
                piece("f4", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("e9", Color.BLACK, PieceType.KING));

        state = apply(state, move("f4", "e4"));

        // Attacking king is check, not chase
        assertEquals(0, state.consecutiveChaseCount(Color.RED));
        assertEquals(1, state.consecutiveCheckCount(Color.RED));
    }

    @Test
    void sixConsecutiveChasesTriggersLoss() {
        // Pre-built state with chase count = 5, one more triggers loss.
        GameState state = stateWithChase(Color.RED, 5, pos("d7"),
                piece("e6", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d7", Color.BLACK, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING));

        state = apply(state, move("e6", "d6"));

        assertEquals(GameStatus.BLACK_WIN, state.status(),
                "Red's 6th consecutive chase → Black wins");
    }

    // ─── Pawn Exception (兵卒例外) ────────────────────────────────────

    @Test
    void pawnChaseSixTimesCausesDraw() {
        GameState state = stateWithChase(Color.RED, 5, pos("d7"),
                piece("e6", Color.RED, PieceType.PAWN),
                piece("a0", Color.RED, PieceType.KING),
                piece("d7", Color.BLACK, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING));

        state = apply(state, move("e6", "d6"));

        assertEquals(GameStatus.DRAW, state.status(),
                "Pawn's 6th consecutive chase → draw");
    }

    @Test
    void pawnCheckSixTimesStillLoses() {
        // Pre-set check count = 5, one more pawn check → 6 → loss.
        // Pawn at d8 moves to e8: attacks e9 (forward/adjacent) = check.
        GameState state = stateWithCheck(Color.RED, 5,
                piece("d8", Color.RED, PieceType.PAWN),
                piece("a0", Color.RED, PieceType.KING),
                piece("e9", Color.BLACK, PieceType.KING));

        state = apply(state, move("d8", "e8"));

        assertEquals(GameStatus.BLACK_WIN, state.status(),
                "Pawn's 6th consecutive check → still loses");
    }

    // ─── Check Resets Chase (长将优先) ─────────────────────────────────

    @Test
    void checkResetsChaseCounterForSameSide() {
        GameState state = initialState(Color.RED,
                piece("e6", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d7", Color.BLACK, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING),
                piece("d9", Color.BLACK, PieceType.ROOK));

        // Red: chase d7
        state = apply(state, move("e6", "d6"));
        assertEquals(1, state.consecutiveChaseCount(Color.RED));

        // Black: move safe rook
        state = apply(state, move("d9", "d8"));

        // Red: now give check — d6→e6.
        // From e6, rook attacks e9 (same file e). From d6, did rook attack e9?
        // d6 to e9: dx=1, dy=3 — not straight → no. So moving d6→e6
        // creates a new attack on e9 → check!
        state = apply(state, move("d6", "e6"));
        assertEquals(1, state.consecutiveCheckCount(Color.RED)); // check counted
        assertEquals(0, state.consecutiveChaseCount(Color.RED)); // chase reset
        assertNull(state.chasedPosition(Color.RED));
    }

    // ─── Counter Isolation (per-side) ─────────────────────────────────

    @Test
    void redChaseCounterPreservedAcrossBlackMoves() {
        GameState state = initialState(Color.RED,
                piece("e6", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d7", Color.BLACK, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING),
                piece("d9", Color.BLACK, PieceType.ROOK));

        // Red: chase
        state = apply(state, move("e6", "d6"));
        assertEquals(1, state.consecutiveChaseCount(Color.RED));

        // Black: safe move 1
        state = apply(state, move("d9", "d8"));
        assertEquals(1, state.consecutiveChaseCount(Color.RED)); // preserved

        // Black: safe move 2 (but it's Red's turn now — wait,
        // after Black moves, it's Red's turn.)
        // Actually: after d9→d8, it's Red's turn. Let me re-check.
        // Red moves e6→d6 → turn=BLACK. Black moves d9→d8 → turn=RED.
        // Now Red: move to e6 (no chase)
        state = apply(state, move("d6", "e6"));
        assertEquals(0, state.consecutiveChaseCount(Color.RED)); // no new attack

        // Red's chase counter correctly reset after non-chase
    }

    @Test
    void blackCountersIndependentOfRed() {
        GameState state = initialState(Color.RED,
                piece("e4", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d9", Color.BLACK, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING));

        // Red: check Black
        state = apply(state, move("e4", "e5"));
        assertEquals(1, state.consecutiveCheckCount(Color.RED));

        // Black: safe move (NOT a check)
        state = apply(state, move("d9", "d8"));
        assertEquals(0, state.consecutiveCheckCount(Color.BLACK));
        assertEquals(1, state.consecutiveCheckCount(Color.RED)); // preserved

        // Red: check again
        state = apply(state, move("e5", "e4"));
        assertEquals(2, state.consecutiveCheckCount(Color.RED));
        assertEquals(0, state.consecutiveCheckCount(Color.BLACK));
    }

    // ─── Edge Cases ────────────────────────────────────────────────────

    @Test
    void counterResetOnCapture() {
        GameState state = initialState(Color.RED,
                piece("d6", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d7", Color.BLACK, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING));

        // Red: capture the chased piece
        state = apply(state, move("d6", "d7"));

        // After capture, target gone → no chase
        assertEquals(0, state.consecutiveChaseCount(Color.RED));
        assertEquals(0, state.noCaptureHalfMoves()); // reset on capture
        assertNull(state.chasedPosition(Color.RED));
    }

    @Test
    void initialStateCountersAreZero() {
        GameState state = GameState.initial();
        assertEquals(0, state.consecutiveCheckCount(Color.RED));
        assertEquals(0, state.consecutiveChaseCount(Color.RED));
        assertEquals(0, state.consecutiveCheckCount(Color.BLACK));
        assertEquals(0, state.consecutiveChaseCount(Color.BLACK));
        assertNull(state.chasedPosition(Color.RED));
        assertNull(state.chasedPosition(Color.BLACK));
    }

    @Test
    void checkDetectionDoesNotAffectLegalMoves() {
        // Rule: players are allowed to not answer check.
        GameState state = initialState(Color.RED,
                piece("e4", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("e9", Color.BLACK, PieceType.KING),
                piece("d9", Color.BLACK, PieceType.ROOK));

        // Red puts Black in check
        state = apply(state, move("e4", "e5"));

        // Black should still have legal moves
        assertFalse(state.status() == GameStatus.PLAYING
                        && engine.legalMoves(state).isEmpty(),
                "Black should have legal moves despite being in check");
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    private GameState apply(GameState state, Move move) {
        ApplyResult result = engine.apply(state, move);
        assertTrue(result.validation().valid(),
                "Move rejected: " + move.source() + "→" + move.destination()
                + " — " + result.validation().message());
        return result.state();
    }

    /** Create a GameState with pre-set chase count for one side. */
    private static GameState stateWithChase(
            Color turn, int chaseCount, Position chasedPos,
            Map.Entry<Position, Piece>... entries) {

        Map<Position, Piece> pieces = new LinkedHashMap<>();
        for (var entry : entries) {
            pieces.put(entry.getKey(), entry.getValue());
        }
        return new GameState(
                new Board(pieces),
                turn,
                0,
                turn == Color.RED ? 0 : 0,
                turn == Color.RED ? chaseCount : 0,
                turn == Color.RED ? chasedPos : null,
                turn == Color.BLACK ? 0 : 0,
                turn == Color.BLACK ? chaseCount : 0,
                turn == Color.BLACK ? chasedPos : null,
                0,
                GameStatus.PLAYING,
                HiddenPiecePool.standard(),
                HiddenPiecePool.standard());
    }

    /** Create a GameState with pre-set check count for one side. */
    private static GameState stateWithCheck(
            Color turn, int checkCount,
            Map.Entry<Position, Piece>... entries) {

        Map<Position, Piece> pieces = new LinkedHashMap<>();
        for (var entry : entries) {
            pieces.put(entry.getKey(), entry.getValue());
        }
        return new GameState(
                new Board(pieces),
                turn,
                0,
                turn == Color.RED ? checkCount : 0,
                0,
                null,
                turn == Color.BLACK ? checkCount : 0,
                0,
                null,
                0,
                GameStatus.PLAYING,
                HiddenPiecePool.standard(),
                HiddenPiecePool.standard());
    }

    @SafeVarargs
    private static GameState initialState(
            Color turn, Map.Entry<Position, Piece>... entries) {
        return stateWithChase(turn, 0, null, entries);
    }

    private static Map.Entry<Position, Piece> piece(
            String coordinate, Color color, PieceType type) {
        return Map.entry(pos(coordinate), Piece.visible(color, type));
    }

    private static Position pos(String coordinate) {
        return Position.parse(coordinate);
    }

    private static Move move(String source, String destination) {
        return new Move(pos(source), pos(destination), 0);
    }

    private static final class FirstChoiceRandom implements RandomGenerator {
        @Override
        public long nextLong() {
            return 0;
        }
    }
}
