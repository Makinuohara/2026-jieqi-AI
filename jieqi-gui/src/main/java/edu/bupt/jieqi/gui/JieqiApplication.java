package edu.bupt.jieqi.gui;

import java.util.List;
import java.util.function.Supplier;
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
    private static Supplier<ServerControl> serverControlFactory = UnsupportedServerControl::new;

    private NetworkLobbyView networkLobbyView;
    private StartServerView startServerView;

    @Override
    public void start(Stage stage) {
        stage.setTitle("揭棋竞技场");
        stage.setScene(new Scene(home(stage), 1180, 760));
        stage.setMinWidth(1050);
        stage.setMinHeight(720);
        stage.show();
    }

    private BorderPane home(Stage stage) {
        Label title = new Label("揭棋竞技场");
        title.getStyleClass().add("title");
        Label subtitle = new Label("2026 年 Java 面向对象大作业");
        subtitle.getStyleClass().add("subtitle");

        VBox actions = new VBox(14);
        actions.setMaxWidth(360);
        for (String mode : List.of(
                "真人对人工智能",
                "本地人工智能对弈",
                "联网对弈与人工智能比赛",
                "启动对弈服务器")) {
            Button button = new Button(mode);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event -> showMode(stage, mode));
            actions.getChildren().add(button);
        }

        VBox content = new VBox(28, title, subtitle, actions);
        content.setAlignment(Pos.CENTER);
        BorderPane root = new BorderPane(content);
        root.setPadding(new Insets(36));
        applyStyles(root);
        return root;
    }

    private void showMode(Stage stage, String mode) {
        if ("真人对人工智能".equals(mode)) {
            stage.getScene().setRoot(new HumanVsAiView(
                    () -> stage.getScene().setRoot(home(stage))));
            return;
        }
        if ("本地人工智能对弈".equals(mode)) {
            stage.getScene().setRoot(new LocalAiVsAiView(
                    () -> stage.getScene().setRoot(home(stage))));
            return;
        }
        if ("联网对弈与人工智能比赛".equals(mode)) {
            if (networkLobbyView == null) {
                networkLobbyView = new NetworkLobbyView(
                        () -> stage.getScene().setRoot(home(stage)));
            }
            stage.getScene().setRoot(networkLobbyView);
            return;
        }
        if ("启动对弈服务器".equals(mode)) {
            if (startServerView == null) {
                startServerView = new StartServerView(
                        () -> stage.getScene().setRoot(home(stage)),
                        serverControlFactory.get());
            }
            stage.getScene().setRoot(startServerView);
            return;
        }
        stage.getScene().setRoot(gameShell(stage, mode));
    }

    private BorderPane gameShell(Stage stage, String mode) {
        VBox left = new VBox(12,
                new Label(mode),
                new Label("红方：未连接"),
                new Label("黑方：未连接"),
                new Label("回合计时：60 秒，另加 5 秒网络容差"));
        left.getStyleClass().add("panel");
        left.setPrefWidth(210);

        GridPane board = new GridPane();
        board.setAlignment(Pos.CENTER);
        for (int rank = 9; rank >= 0; rank--) {
            for (int file = 0; file < 9; file++) {
                Button square = new Button("");
                square.getStyleClass().add("square");
                square.setDisable(true);
                board.add(square, file, 9 - rank);
            }
        }

        ListView<String> moves = new ListView<>();
        moves.getItems().add("初级框架已就绪，完整对局功能仍需继续实现。");
        VBox right = new VBox(10, new Label("走法记录"), moves,
                new Button("暂停"), new Button("单步"), new Button("认输"));
        right.getStyleClass().add("panel");
        right.setPrefWidth(240);
        VBox.setVgrow(moves, Priority.ALWAYS);

        Button back = new Button("返回");
        back.setOnAction(event -> stage.getScene().setRoot(home(stage)));
        HBox header = new HBox(14, back, new Label("当前模式：" + mode));
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

    public static void configureServerControlFactory(Supplier<ServerControl> factory) {
        serverControlFactory = factory == null ? UnsupportedServerControl::new : factory;
    }

    @Override
    public void stop() {
        if (networkLobbyView != null) {
            networkLobbyView.shutdown();
        }
        if (startServerView != null) {
            startServerView.shutdown();
        }
    }

    public static void main(String[] args) {
        launchApp(args);
    }

    private static final class UnsupportedServerControl implements ServerControl {
        @Override
        public void setLogListener(java.util.function.Consumer<String> listener) {
            listener.accept("当前启动方式未注入内嵌服务器实现，请通过 jieqi-app 启动图形界面。");
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public int port() {
            return 8887;
        }

        @Override
        public void start(int port) {
            throw new IllegalStateException("未注入内嵌服务器实现");
        }

        @Override
        public void stop() {
        }
    }
}
