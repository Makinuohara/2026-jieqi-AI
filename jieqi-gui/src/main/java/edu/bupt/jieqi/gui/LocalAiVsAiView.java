package edu.bupt.jieqi.gui;

import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.Position;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

final class LocalAiVsAiView extends BorderPane {
    private final LocalAiVsAiGame game = new LocalAiVsAiGame();
    private final Button[][] squares = new Button[9][10];
    private final Label turnLabel = new Label();
    private final Label statusLabel = new Label();
    private final ListView<String> moveList = new ListView<>();
    private final Button pauseButton = new Button("暂停");
    private final Button stepButton = new Button("单步");
    private final ComboBox<String> speedBox = new ComboBox<>();
    private Timeline nextMoveDelay;
    private boolean aiThinking;
    private boolean autoRunning = true;

    LocalAiVsAiView(Runnable backAction) {
        setPadding(new Insets(18));
        setTop(header(backAction));
        setLeft(informationPanel());
        setCenter(board());
        setRight(recordPanel());
        BorderPane.setMargin(getCenter(), new Insets(16));
        getStylesheets().add(
                LocalAiVsAiView.class.getResource("/edu/bupt/jieqi/gui/app.css").toExternalForm());
        refresh();
        scheduleNextMove();
    }

    private HBox header(Runnable backAction) {
        Button back = new Button("返回");
        back.setOnAction(event -> {
            cancelScheduledMove();
            autoRunning = false;
            backAction.run();
        });
        Label title = new Label("本地人工智能对弈");
        title.getStyleClass().add("section-title");
        HBox header = new HBox(14, back, title);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox informationPanel() {
        Label red = new Label("红方：" + game.redAiName());
        Label black = new Label("黑方：" + game.blackAiName());
        Label help = new Label("双方人工智能会自动轮流走子，可暂停、继续或单步观察。");
        help.setWrapText(true);

        speedBox.getItems().addAll("快速", "正常", "慢速");
        speedBox.setValue("正常");

        VBox panel = new VBox(12,
                red,
                black,
                new Label("播放速度"),
                speedBox,
                turnLabel,
                statusLabel,
                help);
        panel.getStyleClass().add("panel");
        panel.setPrefWidth(220);
        return panel;
    }

    private GridPane board() {
        GridPane board = new GridPane();
        board.setAlignment(Pos.CENTER);
        board.getStyleClass().add("chess-board");
        for (int rank = 9; rank >= 0; rank--) {
            for (int file = 0; file < 9; file++) {
                Position position = new Position(file, rank);
                Button square = new Button();
                square.getStyleClass().add("square");
                square.setDisable(true);
                squares[file][rank] = square;
                board.add(square, file, 9 - rank);
            }
        }
        return board;
    }

    private VBox recordPanel() {
        moveList.setCellFactory(list -> wrappingRecordCell());

        pauseButton.setMaxWidth(Double.MAX_VALUE);
        pauseButton.setOnAction(event -> toggleAutoPlay());

        stepButton.setMaxWidth(Double.MAX_VALUE);
        stepButton.setOnAction(event -> {
            if (aiThinking || game.state().status() != GameStatus.PLAYING) {
                return;
            }
            autoRunning = false;
            cancelScheduledMove();
            refresh();
            runOneMove();
        });

        Button restart = new Button("重新开始");
        restart.setMaxWidth(Double.MAX_VALUE);
        restart.setOnAction(event -> {
            cancelScheduledMove();
            game.restart();
            aiThinking = false;
            autoRunning = true;
            refresh();
            scheduleNextMove();
        });

        VBox panel = new VBox(10, new Label("走法记录"), moveList, pauseButton, stepButton, restart);
        panel.getStyleClass().add("panel");
        panel.setPrefWidth(320);
        panel.setMinWidth(280);
        VBox.setVgrow(moveList, Priority.ALWAYS);
        return panel;
    }

    private ListCell<String> wrappingRecordCell() {
        ListCell<String> cell = new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        };
        cell.setWrapText(true);
        cell.prefWidthProperty().bind(moveList.widthProperty().subtract(24));
        return cell;
    }

    private void toggleAutoPlay() {
        if (aiThinking || game.state().status() != GameStatus.PLAYING) {
            return;
        }
        autoRunning = !autoRunning;
        cancelScheduledMove();
        refresh();
        if (autoRunning) {
            scheduleNextMove();
        }
    }

    private void scheduleNextMove() {
        if (!autoRunning || aiThinking || game.state().status() != GameStatus.PLAYING) {
            return;
        }
        cancelScheduledMove();
        nextMoveDelay = new Timeline(new KeyFrame(selectedDelay(), event -> runOneMove()));
        nextMoveDelay.playFromStart();
    }

    private void cancelScheduledMove() {
        if (nextMoveDelay != null) {
            nextMoveDelay.stop();
            nextMoveDelay = null;
        }
    }

    private void runOneMove() {
        if (aiThinking || game.state().status() != GameStatus.PLAYING) {
            return;
        }
        aiThinking = true;
        refresh();

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                game.performNextMove();
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            aiThinking = false;
            refresh();
            scheduleNextMove();
        });
        task.setOnFailed(event -> {
            aiThinking = false;
            autoRunning = false;
            refresh();
            statusLabel.setText("人工智能运行失败：" + task.getException().getMessage());
        });
        Thread thread = new Thread(task, "jieqi-local-ai-battle");
        thread.setDaemon(true);
        thread.start();
    }

    private Duration selectedDelay() {
        String speed = speedBox.getValue();
        if ("快速".equals(speed)) {
            return Duration.millis(200);
        }
        if ("慢速".equals(speed)) {
            return Duration.seconds(1.2);
        }
        return Duration.millis(600);
    }

    private void refresh() {
        for (int file = 0; file < 9; file++) {
            for (int rank = 0; rank <= 9; rank++) {
                Position position = new Position(file, rank);
                Button square = squares[file][rank];
                square.getStyleClass().removeAll("red-piece", "black-piece");
                Piece piece = game.state().board().pieceAt(position).orElse(null);
                square.setText(PieceTextFormatter.format(piece));
                if (piece != null) {
                    square.getStyleClass().add(
                            piece.owner() == Color.RED ? "red-piece" : "black-piece");
                }
            }
        }

        if (aiThinking) {
            turnLabel.setText("当前回合：" + colorText(game.state().currentTurn()) + "思考中");
        } else {
            turnLabel.setText("当前回合：" + colorText(game.state().currentTurn()));
        }
        statusLabel.setText(HumanVsAiView.statusText(
                game.state().status(),
                game.isInCheck(Color.RED),
                game.isInCheck(Color.BLACK)));
        moveList.getItems().setAll(game.moveRecords());
        if (!moveList.getItems().isEmpty()) {
            moveList.scrollTo(moveList.getItems().size() - 1);
        }

        pauseButton.setText(autoRunning ? "暂停" : "继续");
        pauseButton.setDisable(aiThinking || game.state().status() != GameStatus.PLAYING);
        stepButton.setDisable(aiThinking || game.state().status() != GameStatus.PLAYING);
        speedBox.setDisable(aiThinking);
    }

    private String colorText(Color color) {
        return color == Color.RED ? "红方" : "黑方";
    }
}
