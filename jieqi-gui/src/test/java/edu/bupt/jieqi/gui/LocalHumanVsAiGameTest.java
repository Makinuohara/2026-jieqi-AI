package edu.bupt.jieqi.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.bupt.jieqi.ai.Agent;
import edu.bupt.jieqi.ai.ExpectiminimaxAgent;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.model.Position;
import edu.bupt.jieqi.rules.StandardGameEngine;
import org.junit.jupiter.api.Test;

class LocalHumanVsAiGameTest {
    @Test
    void humanMoveAndAiReplyAdvanceTwoHalfMoves() {
        Agent firstMoveAgent = new Agent() {
            @Override
            public Move chooseMove(PlayerView view, edu.bupt.jieqi.ai.SearchBudget budget) {
                return view.legalMoves().getFirst();
            }

            @Override
            public String name() {
                return "测试人工智能";
            }
        };
        LocalHumanVsAiGame game = new LocalHumanVsAiGame(
                new StandardGameEngine(), firstMoveAgent);
        Move humanMove = new Move(Position.parse("a3"), Position.parse("a4"), 0);

        assertTrue(game.submitHumanMove(humanMove).validation().valid());
        assertTrue(game.state().board().pieceAt(Position.parse("a3")).isEmpty());
        Piece revealed = game.state().board().pieceAt(Position.parse("a4")).orElseThrow();
        assertTrue(revealed.visible());
        assertNotEquals("暗", PieceTextFormatter.format(revealed));
        assertEquals(Color.BLACK, game.state().currentTurn());

        assertTrue(game.performAiMove().isPresent());
        assertEquals(Color.RED, game.state().currentTurn());
        assertEquals(2, game.moveRecords().size());
    }

    @Test
    void resignAndRestartUpdateGameState() {
        LocalHumanVsAiGame game = new LocalHumanVsAiGame();

        game.resignHuman();
        assertEquals(GameStatus.BLACK_WIN, game.state().status());
        assertFalse(game.legalHumanMoves().iterator().hasNext());

        game.restart();
        assertEquals(GameStatus.PLAYING, game.state().status());
        assertEquals(Color.RED, game.state().currentTurn());
        assertTrue(game.moveRecords().isEmpty());
    }

    @Test
    void defaultHumanVsAiUsesSearchAgent() {
        LocalHumanVsAiGame game = new LocalHumanVsAiGame();

        assertTrue(game.ai() instanceof ExpectiminimaxAgent);
    }
}
