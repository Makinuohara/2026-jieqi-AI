package edu.bupt.jieqi.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JieqiApplicationTextTest {
    @Test
    void userVisibleTextIsChinese() throws IOException {
        String source;
        try (var files = Files.walk(Path.of("src/main/java/edu/bupt/jieqi/gui"))) {
            source = files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> read(path))
                    .reduce("", String::concat);
        }

        for (String expected : new String[] {
                "揭棋竞技场",
                "真人对人工智能",
                "本地人工智能对弈",
                "联网对弈与人工智能比赛",
                "启动对弈服务器",
                "红方：未连接",
                "黑方：未连接",
                "走法记录",
                "开始",
                "暂停",
                "单步",
                "认输",
                "保存棋谱",
                "查看复盘",
                "打开棋谱",
                "返回",
                "重新开始",
                "当前回合：",
                "人工智能思考中",
                "红方被将军",
                "黑方被将军"
        }) {
            assertTrue(source.contains(expected), () -> "缺少中文界面文字：" + expected);
        }

        for (String forbidden : new String[] {
                "Jieqi Arena",
                "Human vs AI",
                "Local AI vs AI",
                "Network Match / AI Arena",
                "Start Server",
                "Red: not connected",
                "Black: not connected",
                "Move record",
                "Pause",
                "Single step",
                "Resign",
                "Back",
                "Mode:"
        }) {
            assertFalse(source.contains(forbidden), () -> "仍存在英文界面文字：" + forbidden);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
