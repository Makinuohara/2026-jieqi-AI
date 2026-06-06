package edu.bupt.jieqi.gui;

import java.util.List;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class JieqiApplication extends Application {
    @Override
    public void start(Stage stage) {
        stage.setTitle("Jieqi Arena");
        stage.setScene(new Scene(home(stage), 980, 680));
        stage.show();
    }

    private BorderPane home(Stage stage) {
        Label title = new Label("Jieqi Arena");
        title.getStyleClass().add("title");
        Label subtitle = new Label("2026 Java OOP project framework");
        subtitle.getStyleClass().add("subtitle");

        VBox actions = new VBox(14);
        actions.setMaxWidth(360);
        for (String mode : List.of("Human vs AI", "Local AI vs AI", "Network Match / AI Arena",
                "Start Server")) {
            Button button = new Button(mode);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event -> stage.getScene().setRoot(gameShell(stage, mode)));
            actions.getChildren().add(button);
        }

        VBox content = new VBox(28, title, subtitle, actions);
        content.setAlignment(Pos.CENTER);
        BorderPane root = new BorderPane(content);
        root.setPadding(new Insets(36));
        applyStyles(root);
        return root;
    }

    private BorderPane gameShell(Stage stage, String mode) {
        VBox left = new VBox(12,
                new Label(mode),
                new Label("Red: not connected"),
                new Label("Black: not connected"),
                new Label("Turn clock: 60s + 5s grace"));
        left.getStyleClass().add("panel");
        left.setPrefWidth(210);

        GridPane board = new GridPane();
        board.setAlignment(Pos.CENTER);
        for (int rank = 9; rank >= 0; rank--) {
            for (int file = 0; file < 9; file++) {
                Button square = new Button(String.valueOf((char) ('a' + file)) + rank);
                square.getStyleClass().add("square");
                square.setDisable(true);
                board.add(square, file, 9 - rank);
            }
        }

        ListView<String> moves = new ListView<>();
        moves.getItems().add("Framework ready. Full game wiring is a team task.");
        VBox right = new VBox(10, new Label("Move record"), moves,
                new Button("Pause"), new Button("Single step"), new Button("Resign"));
        right.getStyleClass().add("panel");
        right.setPrefWidth(240);
        VBox.setVgrow(moves, Priority.ALWAYS);

        Button back = new Button("Back");
        back.setOnAction(event -> stage.getScene().setRoot(home(stage)));
        HBox header = new HBox(14, back, new Label("Mode: " + mode));
        header.setAlignment(Pos.CENTER_LEFT);

        BorderPane root = new BorderPane(board, header, right, null, left);
        root.setPadding(new Insets(18));
        BorderPane.setMargin(board, new Insets(16));
        applyStyles(root);
        return root;
    }

    private void applyStyles(BorderPane root) {
        root.getStylesheets().add(
                JieqiApplication.class.getResource("/edu/bupt/jieqi/gui/app.css").toExternalForm());
    }

    public static void launchApp(String[] args) {
        launch(args);
    }

    public static void main(String[] args) {
        launchApp(args);
    }
}

