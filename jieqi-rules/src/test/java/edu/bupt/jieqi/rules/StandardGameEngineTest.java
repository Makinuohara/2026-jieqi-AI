package edu.bupt.jieqi.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class StandardGameEngineTest {
    private final GameEngine engine = new StandardGameEngine(new FirstChoiceRandom());

    @Test
    void rejectsFlipInPlace() {
        ApplyResult result = engine.apply(
                GameState.initial(), move("a0", "a0"));

        assertFalse(result.validation().valid());
        assertEquals(MoveError.FLIP_IN_PLACE_FORBIDDEN, result.validation().error());
    }

    @Test
    void rookMovesStraightButCannotJump() {
        GameState clear = state(Color.RED,
                piece("a0", Color.RED, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING));
        GameState blocked = state(Color.RED,
                piece("a0", Color.RED, PieceType.ROOK),
                piece("a1", Color.RED, PieceType.PAWN),
                piece("e9", Color.BLACK, PieceType.KING));

        assertTrue(engine.legalMoves(clear).contains(move("a0", "a5")));
        assertFalse(engine.legalMoves(clear).contains(move("a0", "b1")));
        assertFalse(engine.legalMoves(blocked).contains(move("a0", "a2")));
    }

    @Test
    void knightCannotMoveWhenItsLegIsBlocked() {
        GameState clear = state(Color.RED,
                piece("b0", Color.RED, PieceType.KNIGHT),
                piece("e9", Color.BLACK, PieceType.KING));
        GameState blocked = state(Color.RED,
                piece("b0", Color.RED, PieceType.KNIGHT),
                piece("b1", Color.RED, PieceType.PAWN),
                piece("e9", Color.BLACK, PieceType.KING));

        assertTrue(engine.legalMoves(clear).contains(move("b0", "c2")));
        assertFalse(engine.legalMoves(blocked).contains(move("b0", "c2")));
    }

    @Test
    void bishopCannotCrossBlockedEyeButMayCrossRiver() {
        GameState clear = state(Color.RED,
                piece("c4", Color.RED, PieceType.BISHOP),
                piece("e9", Color.BLACK, PieceType.KING));
        GameState blocked = state(Color.RED,
                piece("c4", Color.RED, PieceType.BISHOP),
                piece("d5", Color.RED, PieceType.PAWN),
                piece("e9", Color.BLACK, PieceType.KING));

        assertTrue(engine.legalMoves(clear).contains(move("c4", "e6")));
        assertFalse(engine.legalMoves(blocked).contains(move("c4", "e6")));
    }

    @Test
    void cannonNeedsExactlyOneScreenWhenCapturing() {
        GameState oneScreen = state(Color.RED,
                piece("a0", Color.RED, PieceType.CANNON),
                piece("a2", Color.RED, PieceType.PAWN),
                piece("a5", Color.BLACK, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING));
        GameState noScreen = state(Color.RED,
                piece("a0", Color.RED, PieceType.CANNON),
                piece("a5", Color.BLACK, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING));

        assertTrue(engine.legalMoves(oneScreen).contains(move("a0", "a5")));
        assertFalse(engine.legalMoves(noScreen).contains(move("a0", "a5")));
    }

    @Test
    void pawnMovesSidewaysOnlyAfterCrossingRiver() {
        GameState beforeRiver = state(Color.RED,
                piece("e4", Color.RED, PieceType.PAWN),
                piece("e9", Color.BLACK, PieceType.KING));
        GameState afterRiver = state(Color.RED,
                piece("e5", Color.RED, PieceType.PAWN),
                piece("e9", Color.BLACK, PieceType.KING));

        assertTrue(engine.legalMoves(beforeRiver).contains(move("e4", "e5")));
        assertFalse(engine.legalMoves(beforeRiver).contains(move("e4", "f4")));
        assertTrue(engine.legalMoves(afterRiver).contains(move("e5", "f5")));
        assertFalse(engine.legalMoves(afterRiver).contains(move("e5", "e4")));
    }

    @Test
    void visibleGuardMayLeavePalace() {
        GameState state = state(Color.RED,
                piece("f2", Color.RED, PieceType.GUARD),
                piece("e9", Color.BLACK, PieceType.KING));

        assertTrue(engine.legalMoves(state).contains(move("f2", "g3")));
    }

    @Test
    void kingStaysInPalaceAndCannotExposeFacingKings() {
        GameState state = state(Color.RED,
                piece("e0", Color.RED, PieceType.KING),
                piece("e9", Color.BLACK, PieceType.KING));

        assertFalse(engine.legalMoves(state).contains(move("e0", "e1")));
        assertTrue(engine.legalMoves(state).contains(move("e0", "d0")));
        assertFalse(engine.legalMoves(state).contains(move("e0", "f0"))
                && engine.legalMoves(state).contains(move("e0", "g0")));
    }

    @Test
    void hiddenPieceMovesByVirtualTypeThenRevealsFromPool() {
        Map<Position, Piece> pieces = new LinkedHashMap<>();
        pieces.put(pos("a0"), Piece.hidden(Color.RED, PieceType.ROOK));
        pieces.put(pos("e9"), Piece.visible(Color.BLACK, PieceType.KING));
        GameState state = state(Color.RED, pieces);

        ApplyResult result = engine.apply(state, move("a0", "a1"));

        assertTrue(result.validation().valid());
        Piece revealed = result.state().board().pieceAt(pos("a1")).orElseThrow();
        assertTrue(revealed.visible());
        assertEquals(PieceType.ROOK, revealed.actualType());
        assertEquals(14, result.state().redHiddenPool().total());
        assertTrue(result.events().stream().anyMatch(GameEvent.PieceRevealed.class::isInstance));
    }

    @Test
    void capturingKingEndsGameImmediately() {
        GameState state = state(Color.RED,
                piece("e8", Color.RED, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING),
                piece("e0", Color.RED, PieceType.KING));

        ApplyResult result = engine.apply(state, move("e8", "e9"));

        assertTrue(result.validation().valid());
        assertEquals(GameStatus.RED_WIN, result.state().status());
    }

    @Test
    void rejectsMovementNotGeneratedAsLegal() {
        GameState state = state(Color.RED,
                piece("a0", Color.RED, PieceType.ROOK),
                piece("e9", Color.BLACK, PieceType.KING));

        ApplyResult result = engine.apply(state, move("a0", "b1"));

        assertFalse(result.validation().valid());
        assertEquals(MoveError.ILLEGAL_PIECE_MOVEMENT, result.validation().error());
    }

    @SafeVarargs
    private static GameState state(Color turn, Map.Entry<Position, Piece>... entries) {
        Map<Position, Piece> pieces = new LinkedHashMap<>();
        for (Map.Entry<Position, Piece> entry : entries) {
            pieces.put(entry.getKey(), entry.getValue());
        }
        return state(turn, pieces);
    }

    private static GameState state(Color turn, Map<Position, Piece> pieces) {
        return new GameState(
                new Board(pieces),
                turn,
                0,
                0,
                0,
                0,
                GameStatus.PLAYING,
                HiddenPiecePool.standard(),
                HiddenPiecePool.standard());
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
