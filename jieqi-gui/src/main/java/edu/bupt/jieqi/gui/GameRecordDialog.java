package edu.bupt.jieqi.gui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Window;

final class GameRecordDialog {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private GameRecordDialog() {
    }

    static void showCurrent(Node owner, String mode, String result, List<String> records) {
        showText(owner, "棋谱复盘", format(mode, result, records));
    }

    static void saveCurrent(Node owner, String mode, String result, List<String> records) {
        FileChooser chooser = recordFileChooser();
        chooser.setInitialFileName("jieqi-" + LocalDateTime.now().format(FILE_TIME) + ".txt");
        File file = chooser.showSaveDialog(window(owner));
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), format(mode, result, records), StandardCharsets.UTF_8);
            info(owner, "棋谱已保存", file.getAbsolutePath());
        } catch (IOException exception) {
            error(owner, "保存棋谱失败", exception.getMessage());
        }
    }

    static void openSaved(Node owner) {
        File file = recordFileChooser().showOpenDialog(window(owner));
        if (file == null) {
            return;
        }
        try {
            showText(owner, "棋谱复盘", Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            error(owner, "打开棋谱失败", exception.getMessage());
        }
    }

    static String format(String mode, String result, List<String> records) {
        StringBuilder text = new StringBuilder()
                .append("模式：").append(mode).append(System.lineSeparator())
                .append("结果：").append(result).append(System.lineSeparator())
                .append("步数：").append(records.size()).append(System.lineSeparator())
                .append(System.lineSeparator());
        for (int index = 0; index < records.size(); index++) {
            text.append(index + 1).append(". ").append(records.get(index)).append(System.lineSeparator());
        }
        return text.toString();
    }

    private static void showText(Node owner, String title, String text) {
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefColumnCount(56);
        area.setPrefRowCount(24);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.initOwner(window(owner));
        dialog.getDialogPane().setContent(area);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private static FileChooser recordFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择棋谱文件");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("揭棋棋谱文本", "*.txt"));
        return chooser;
    }

    private static Window window(Node owner) {
        return owner.getScene() == null ? null : owner.getScene().getWindow();
    }

    private static void info(Node owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.initOwner(window(owner));
        alert.showAndWait();
    }

    private static void error(Node owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.initOwner(window(owner));
        alert.showAndWait();
    }
}
