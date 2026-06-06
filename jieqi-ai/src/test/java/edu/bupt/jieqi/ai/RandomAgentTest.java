package edu.bupt.jieqi.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.bupt.jieqi.model.Board;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.model.Position;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RandomAgentTest {
    @Test
    void choosesOnlyFromProvidedLegalMoves() {
        Move first = new Move(Position.parse("a0"), Position.parse("a1"), 0);
        Move second = new Move(Position.parse("b0"), Position.parse("c2"), 0);
        PlayerView view = new PlayerView(Board.initial(), Color.RED, Color.RED, List.of(first, second));

        Move chosen = new RandomAgent(new Random(7)).chooseMove(
                view, new SearchBudget(Duration.ofSeconds(1), 1));

        assertTrue(view.legalMoves().contains(chosen));
    }
}

