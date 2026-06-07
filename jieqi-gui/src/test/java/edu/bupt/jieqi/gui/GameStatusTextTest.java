package edu.bupt.jieqi.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.bupt.jieqi.model.GameStatus;
import org.junit.jupiter.api.Test;

class GameStatusTextTest {
    @Test
    void showsBlackKingStillInCheckWhenTurnHasReturnedToRed() {
        assertEquals(
                "对局状态：黑方被将军",
                HumanVsAiView.statusText(
                        GameStatus.PLAYING, false, true));
    }

    @Test
    void showsCurrentSideInCheckWithoutCaptureHint() {
        assertEquals(
                "对局状态：红方被将军",
                HumanVsAiView.statusText(
                        GameStatus.PLAYING, true, false));
    }
}
