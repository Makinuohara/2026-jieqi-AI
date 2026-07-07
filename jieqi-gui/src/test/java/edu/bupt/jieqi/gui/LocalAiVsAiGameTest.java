package edu.bupt.jieqi.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.bupt.jieqi.ai.Agent;
import edu.bupt.jieqi.ai.SearchBudget;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.PlayerView;
import edu.bupt.jieqi.rules.StandardGameEngine;
import org.junit.jupiter.api.Test;

class LocalAiVsAiGameTest {
    @Test
    void aiBattleAdvancesSeveralHalfMoves() {
        Agent firstMoveAgent = new Agent() {
            @Override
            public Move chooseMove(PlayerView view, SearchBudget budget) {
                return view.legalMoves().getFirst();
            }

            @Override
            public String name() {
                return "首步人工智能";
            }
        };
        LocalAiVsAiGame game = new LocalAiVsAiGame(
                new StandardGameEngine(), firstMoveAgent, firstMoveAgent);

        for (int step = 0; step < 6; step++) {
            assertTrue(game.performNextMove().isPresent(), "第 " + (step + 1) + " 步应成功推进");
        }

        assertEquals(6, game.moveRecords().size());
        assertTrue(game.state().status() == GameStatus.PLAYING
                || game.state().status() == GameStatus.RED_WIN
                || game.state().status() == GameStatus.BLACK_WIN
                || game.state().status() == GameStatus.DRAW);
        assertTrue(game.moveRecords().getFirst().contains("首步人工智能"));
    }

    @Test
    void battleCanRestartFromFreshState() {
        LocalAiVsAiGame game = new LocalAiVsAiGame();

        assertTrue(game.performNextMove().isPresent());
        assertFalse(game.moveRecords().isEmpty());
        assertNotEquals(Color.RED, game.state().currentTurn());

        game.restart();

        assertEquals(GameStatus.PLAYING, game.state().status());
        assertEquals(Color.RED, game.state().currentTurn());
        assertTrue(game.moveRecords().isEmpty());
    }

    @Test
    void battleCanUseSelectedAiModesForBothSides() {
        LocalAiVsAiGame game = new LocalAiVsAiGame(
                LocalAiVsAiGame.AiMode.RANDOM.createAgent(),
                LocalAiVsAiGame.AiMode.GREEDY.createAgent());

        assertEquals("Random", game.redAiName());
        assertEquals("Greedy", game.blackAiName());

        assertTrue(game.performNextMove().isPresent());
        assertTrue(game.performNextMove().isPresent());

        assertTrue(game.moveRecords().get(0).contains("Random"));
        assertTrue(game.moveRecords().get(1).contains("Greedy"));
    }

    @Test
    void experimentReportIncludesMatchupStatistics() {
        String report = LocalAiVsAiExperiment.run(new LocalAiVsAiExperiment.Config(
                1,
                1,
                50,
                java.util.List.of(LocalAiVsAiGame.AiMode.RANDOM)));

        assertTrue(report.contains("本地 AI 对 AI 批量实验"));
        assertTrue(report.contains("红方AI"));
        assertTrue(report.contains("黑方AI"));
        assertTrue(report.contains("平均步数"));
        assertTrue(report.contains("非法"));
        assertTrue(report.contains("随机 AI"));
    }
}
