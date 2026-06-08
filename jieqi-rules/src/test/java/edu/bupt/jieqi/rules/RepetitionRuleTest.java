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

    // ─── Integration: real 6-chase sequence from count 0 ──────────────

    @Test
    void realSixConsecutiveChasesFromZero() {
        // Red rook oscillates d5↔e5, Black rook oscillates d7↔e7.
        // Each Red move creates a new attack on the SAME Black rook
        // (the Black rook moves between Red's turns to escape,
        //  and Red follows). This verifies the chase counter can
        // genuinely accumulate to 6 through alternating play.
        GameState state = initialState(Color.RED,
                piece("e5", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d7", Color.BLACK, PieceType.ROOK),
                piece("i9", Color.BLACK, PieceType.KING));

        // 6 Red chases with Black evasions interleaved
        for (int i = 0; i < 6; i++) {
            // Red: chase (alternate d5↔e5, Black's rook alternates d7↔e7)
            String rFrom = (i % 2 == 0) ? "e5" : "d5";
            String rTo = (i % 2 == 0) ? "d5" : "e5";
            state = apply(state, move(rFrom, rTo));
            assertEquals(i + 1, state.consecutiveChaseCount(Color.RED),
                    "Chase counter should be " + (i + 1) + " after Red move " + (i + 1));

            if (state.status() != GameStatus.PLAYING) break;

            // Black: move chased rook to evade (alternate d7↔e7)
            String bFrom = (i % 2 == 0) ? "d7" : "e7";
            String bTo = (i % 2 == 0) ? "e7" : "d7";
            state = apply(state, move(bFrom, bTo));
            assertEquals(i + 1, state.consecutiveChaseCount(Color.RED),
                    "Red's chase counter preserved across Black's move " + (i + 1));
        }

        assertEquals(GameStatus.BLACK_WIN, state.status(),
                "Red's 6th consecutive chase → Black wins");
    }

    @Test
    void realSixConsecutivePawnChasesFromZero() {
        // Red pawn oscillates e5↔d5. Black rook oscillates d6↔e6.
        // Pawn at d5 attacks d6 (forward 1 step); pawn at e5 attacks e6.
        GameState state = initialState(Color.RED,
                piece("e5", Color.RED, PieceType.PAWN),
                piece("a0", Color.RED, PieceType.KING),
                piece("d6", Color.BLACK, PieceType.ROOK),
                piece("i9", Color.BLACK, PieceType.KING));

        for (int i = 0; i < 6; i++) {
            String rFrom = (i % 2 == 0) ? "e5" : "d5";
            String rTo = (i % 2 == 0) ? "d5" : "e5";
            state = apply(state, move(rFrom, rTo));
            assertEquals(i + 1, state.consecutiveChaseCount(Color.RED));

            if (state.status() != GameStatus.PLAYING) break;

            String bFrom = (i % 2 == 0) ? "d6" : "e6";
            String bTo = (i % 2 == 0) ? "e6" : "d6";
            state = apply(state, move(bFrom, bTo));
        }

        assertEquals(GameStatus.DRAW, state.status(),
                "Pawn's 6th consecutive chase → draw");
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

    // ─── Piece Identity Tracking (chasedPieceType) ──────────────────────

    @Test
    void differentPieceChaseAfterOriginalMoved_resetsCounter() {
        // Red chases Black Rook at d7, Black moves Rook away to e7.
        // Red then chases Black Cannon at f8 (was there all along).
        // Counter must reset to 1 — different piece.
        GameState state = initialState(Color.RED,
                piece("a1", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d7", Color.BLACK, PieceType.ROOK),
                piece("f8", Color.BLACK, PieceType.CANNON),
                piece("i9", Color.BLACK, PieceType.KING));

        // Red 1: a1→d1, attacks Black Rook at d7
        state = apply(state, move("a1", "d1"));
        assertEquals(1, state.consecutiveChaseCount(Color.RED));
        assertEquals(pos("d7"), state.chasedPosition(Color.RED));
        assertEquals(PieceType.ROOK, state.chasedPieceType(Color.RED));

        // Black: d7→e7 (moves the chased rook away)
        state = apply(state, move("d7", "e7"));

        // Red 2: d1→f1, attacks Black Cannon at f8 (different piece!)
        state = apply(state, move("d1", "f1"));
        assertEquals(1, state.consecutiveChaseCount(Color.RED),
                "Chasing a different piece → counter resets to 1");
        assertEquals(pos("f8"), state.chasedPosition(Color.RED));
        assertEquals(PieceType.CANNON, state.chasedPieceType(Color.RED));
    }

    @Test
    void samePieceChaseAfterMove_continuesCounter() {
        // Red chases Black Rook. Black moves same rook to evade.
        // Red follows and chases same rook at new position.
        GameState state = initialState(Color.RED,
                piece("e5", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d7", Color.BLACK, PieceType.ROOK),
                piece("i9", Color.BLACK, PieceType.KING));

        // Red 1: e5→d5, now attacks Black rook at d7 (from e5→d7: dx=1,dy=2 → not straight → wasn't attacking)
        state = apply(state, move("e5", "d5"));
        assertEquals(1, state.consecutiveChaseCount(Color.RED));
        assertEquals(pos("d7"), state.chasedPosition(Color.RED));
        assertEquals(PieceType.ROOK, state.chasedPieceType(Color.RED));

        // Black: d7→e7 (escapes)
        state = apply(state, move("d7", "e7"));

        // Red 2: d5→e5, now attacks Black rook at e7 (from d5→e7: dx=1,dy=2 → not straight → wasn't attacking)
        state = apply(state, move("d5", "e5"));
        assertEquals(2, state.consecutiveChaseCount(Color.RED),
                "Same piece at new position → counter continues");
        assertEquals(pos("e7"), state.chasedPosition(Color.RED));
        assertEquals(PieceType.ROOK, state.chasedPieceType(Color.RED));
    }

    @Test
    void chasePieceTypeIsNullWhenChaseResets() {
        GameState state = initialState(Color.RED,
                piece("e5", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("d7", Color.BLACK, PieceType.ROOK),
                piece("i9", Color.BLACK, PieceType.KING));

        // Red: e5→d5, attacks Black rook at d7 (new attack)
        state = apply(state, move("e5", "d5"));
        assertEquals(PieceType.ROOK, state.chasedPieceType(Color.RED));
        assertEquals(1, state.consecutiveChaseCount(Color.RED));

        // Black: d7→d8 (move rook away)
        state = apply(state, move("d7", "d8"));

        // Red: d5→a5, moves to a-file — no longer attacks any Black piece
        state = apply(state, move("d5", "a5"));
        assertEquals(0, state.consecutiveChaseCount(Color.RED));
        assertNull(state.chasedPieceType(Color.RED));
        assertNull(state.chasedPosition(Color.RED));
    }

    @Test
    void blackChaseCounterWorksSymmetrically() {
        // Black rook chases Red rook — mirror test for Black-side tracking
        GameState state = initialState(Color.BLACK,
                piece("d4", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING),
                piece("e7", Color.BLACK, PieceType.ROOK),
                piece("i9", Color.BLACK, PieceType.KING));

        // Black 1: e7→d7, now attacks Red rook at d4 (from e7→d4: dx=1,dy=3 not straight)
        state = apply(state, move("e7", "d7"));
        assertEquals(1, state.consecutiveChaseCount(Color.BLACK));
        assertEquals(pos("d4"), state.chasedPosition(Color.BLACK));
        assertEquals(PieceType.ROOK, state.chasedPieceType(Color.BLACK));

        // Red: d4→e4 (escapes)
        state = apply(state, move("d4", "e4"));
        assertEquals(1, state.consecutiveChaseCount(Color.BLACK)); // preserved

        // Black 2: d7→e7, attacks Red rook at e4 (new position, same piece)
        state = apply(state, move("d7", "e7"));
        assertEquals(2, state.consecutiveChaseCount(Color.BLACK),
                "Black chase counter continues for same piece");
        assertEquals(pos("e4"), state.chasedPosition(Color.BLACK));
        assertEquals(PieceType.ROOK, state.chasedPieceType(Color.BLACK));
    }

    @Test
    void sixConsecutiveBlackChasesTriggersRedWin() {
        // Pre-set Black chase count = 5, one more triggers Red win.
        // Black rook at e6 moves to d6, creating a new attack on d4.
        GameState state = stateWithChase(Color.BLACK, 5, pos("d4"),
                piece("e6", Color.BLACK, PieceType.ROOK),
                piece("i9", Color.BLACK, PieceType.KING),
                piece("d4", Color.RED, PieceType.ROOK),
                piece("a0", Color.RED, PieceType.KING));

        state = apply(state, move("e6", "d6"));

        assertEquals(GameStatus.RED_WIN, state.status(),
                "Black's 6th consecutive chase → Red wins");
    }

    @Test
    void initialStateChasedPieceTypesAreNull() {
        GameState state = GameState.initial();
        assertNull(state.chasedPieceType(Color.RED));
        assertNull(state.chasedPieceType(Color.BLACK));
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
        PieceType chasedType = null;
        for (var entry : entries) {
            pieces.put(entry.getKey(), entry.getValue());
            if (chasedPos != null && entry.getKey().equals(chasedPos)) {
                chasedType = entry.getValue().movementType();
            }
        }
        return new GameState(
                new Board(pieces),
                turn,
                0,
                turn == Color.RED ? 0 : 0,
                turn == Color.RED ? chaseCount : 0,
                turn == Color.RED ? chasedPos : null,
                turn == Color.RED ? chasedType : null,
                turn == Color.BLACK ? 0 : 0,
                turn == Color.BLACK ? chaseCount : 0,
                turn == Color.BLACK ? chasedPos : null,
                turn == Color.BLACK ? chasedType : null,
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
                null,
                turn == Color.BLACK ? checkCount : 0,
                0,
                null,
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
