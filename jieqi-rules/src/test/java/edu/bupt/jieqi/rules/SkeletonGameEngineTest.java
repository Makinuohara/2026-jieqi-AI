package edu.bupt.jieqi.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Position;
import org.junit.jupiter.api.Test;

class SkeletonGameEngineTest {
    @Test
    void rejectsFlipInPlace() {
        GameEngine engine = new SkeletonGameEngine();
        Position position = Position.parse("a0");

        ApplyResult result = engine.apply(GameState.initial(), new Move(position, position, 0));

        assertFalse(result.validation().valid());
        assertEquals(MoveError.FLIP_IN_PLACE_FORBIDDEN, result.validation().error());
    }
}

