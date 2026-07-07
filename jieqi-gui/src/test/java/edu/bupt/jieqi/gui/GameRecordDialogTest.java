package edu.bupt.jieqi.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GameRecordDialogTest {
    @Test
    void recordTextIncludesModeResultAndNumberedMoves() {
        String text = GameRecordDialog.format(
                "真人对搜索人工智能",
                "对局结束：红方获胜",
                List.of("红方：1列1行 → 1列2行", "黑方：9列10行 → 9列9行"));

        assertTrue(text.contains("模式：真人对搜索人工智能"));
        assertTrue(text.contains("结果：对局结束：红方获胜"));
        assertTrue(text.contains("步数：2"));
        assertTrue(text.contains("1. 红方：1列1行 → 1列2行"));
        assertTrue(text.contains("2. 黑方：9列10行 → 9列9行"));
    }
}
