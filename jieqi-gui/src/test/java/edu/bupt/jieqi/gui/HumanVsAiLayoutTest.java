package edu.bupt.jieqi.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HumanVsAiLayoutTest {
    @Test
    void chessSquareKeepsEnoughSpaceForChinesePieceName() throws IOException {
        String css = Files.readString(
                Path.of("src/main/resources/edu/bupt/jieqi/gui/app.css"),
                StandardCharsets.UTF_8);

        assertTrue(css.contains("-fx-padding: 0;"), "棋格必须覆盖全局按钮内边距");
        assertTrue(css.contains("-fx-text-overrun: clip;"), "棋格不得用省略号代替棋名");
    }

    @Test
    void moveRecordsUseWrappingListCells() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/edu/bupt/jieqi/gui/HumanVsAiView.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("setWrapText(true)"), "走法记录必须支持自动换行");
        assertTrue(source.contains("setCellFactory"), "走法记录必须使用自定义列表单元格");
    }
}
