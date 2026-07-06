package edu.bupt.jieqi.gui;

import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

final class LocalAiVsAiView extends BorderPane {
    private LocalAiVsAiGame game = new LocalAiVsAiGame();
    private final Button[][] squares = new Button[9][10];
    private final Label turnLabel = new Label();
    private final Label statusLabel = new Label();
    private final Label checkStatusLabel = new Label();
    private final ListView<String> moveList = new ListView<>();
    private final Button pauseButton = new Button("暂停");
    private final Button stepButton = new Button("单步");
    private final ComboBox<LocalAiVsAiGame.AiMode> redAiBox = new ComboBox<>();
    private final ComboBox<LocalAiVsAiGame.AiMode> blackAiBox = new ComboBox<>();
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
        Label help = new Label("双方人工智能会自动轮流走子，可暂停、继续或单步观察。");
        help.setWrapText(true);

        redAiBox.getItems().addAll(LocalAiVsAiGame.AiMode.values());
        redAiBox.setValue(LocalAiVsAiGame.AiMode.GREEDY);
        redAiBox.setMaxWidth(Double.MAX_VALUE);
        redAiBox.setOnAction(event -> restartWithSelectedAgents());

        blackAiBox.getItems().addAll(LocalAiVsAiGame.AiMode.values());
        blackAiBox.setValue(LocalAiVsAiGame.AiMode.RANDOM);
        blackAiBox.setMaxWidth(Double.MAX_VALUE);
        blackAiBox.setOnAction(event -> restartWithSelectedAgents());

        speedBox.getItems().addAll("快速", "正常", "慢速");
        speedBox.setValue("正常");
        checkStatusLabel.getStyleClass().add("check-status");
        checkStatusLabel.setWrapText(true);

        VBox panel = new VBox(12,
                new Label("红方 AI"),
                redAiBox,
                new Label("黑方 AI"),
                blackAiBox,
                new Label("播放速度"),
                speedBox,
                turnLabel,
                statusLabel,
                checkStatusLabel,
                help);
        panel.getStyleClass().add("panel");
        panel.setPrefWidth(220);
        return panel;
    }

    private StackPane board() {
        return BoardViewFactory.createBoard(squares, null, false);
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

    private void restartWithSelectedAgents() {
        if (redAiBox.getValue() == null || blackAiBox.getValue() == null) {
            return;
        }
        cancelScheduledMove();
        game = new LocalAiVsAiGame(
                redAiBox.getValue().createAgent(),
                blackAiBox.getValue().createAgent());
        aiThinking = false;
        autoRunning = true;
        refresh();
        scheduleNextMove();
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
        edu.bupt.jieqi.model.Move lastMove = game.lastMove().orElse(null);
        for (int file = 0; file < 9; file++) {
            for (int rank = 0; rank <= 9; rank++) {
                Position position = new Position(file, rank);
                Button square = squares[file][rank];
                square.getStyleClass().removeAll(
                        "red-piece", "black-piece", "occupied-piece", "hidden-piece", "check-target",
                        "last-move-from", "last-move-to");
                Piece piece = game.state().board().pieceAt(position).orElse(null);
                square.setText(PieceTextFormatter.format(piece));
                if (piece != null) {
                    square.getStyleClass().add("occupied-piece");
                    square.getStyleClass().add(
                            piece.owner() == Color.RED ? "red-piece" : "black-piece");
                    if (!piece.visible()) {
                        square.getStyleClass().add("hidden-piece");
                    }
                }
                if (lastMove != null && lastMove.source().equals(position)) {
                    square.getStyleClass().add("last-move-from");
                }
                if (lastMove != null && lastMove.destination().equals(position)) {
                    square.getStyleClass().add("last-move-to");
                }
                if (piece != null && piece.actualType() == PieceType.KING
                        && ((piece.owner() == Color.RED && game.isInCheck(Color.RED))
                        || (piece.owner() == Color.BLACK && game.isInCheck(Color.BLACK)))) {
                    square.getStyleClass().add("check-target");
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
        checkStatusLabel.setText(HumanVsAiView.checkStatusText(
                game.isInCheck(Color.RED),
                game.isInCheck(Color.BLACK)));
        moveList.getItems().setAll(game.moveRecords());
        if (!moveList.getItems().isEmpty()) {
            moveList.scrollTo(moveList.getItems().size() - 1);
        }

        pauseButton.setText(autoRunning ? "暂停" : "继续");
        pauseButton.setDisable(aiThinking || game.state().status() != GameStatus.PLAYING);
        stepButton.setDisable(aiThinking || game.state().status() != GameStatus.PLAYING);
        redAiBox.setDisable(aiThinking);
        blackAiBox.setDisable(aiThinking);
        speedBox.setDisable(aiThinking);
    }

    private String colorText(Color color) {
        return color == Color.RED ? "红方" : "黑方";
    }
}
