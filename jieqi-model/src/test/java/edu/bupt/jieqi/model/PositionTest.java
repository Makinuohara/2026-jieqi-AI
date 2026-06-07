package edu.bupt.jieqi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PositionTest {
    @Test
    void parsesPublicCoordinates() {
        assertEquals(new Position(1, 3), Position.parse("b3"));
        assertEquals("b3", new Position(1, 3).toString());
    }

    @Test
    void rejectsCoordinatesOutsideBoard() {
        assertThrows(IllegalArgumentException.class, () -> Position.parse("j0"));
        assertThrows(IllegalArgumentException.class, () -> Position.parse("a10"));
    }
}
