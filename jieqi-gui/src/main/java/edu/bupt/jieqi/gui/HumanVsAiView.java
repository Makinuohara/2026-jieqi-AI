package edu.bupt.jieqi.gui;

import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.Position;
import java.util.List;
import java.util.Optional;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

final class HumanVsAiView extends BorderPane {
    private final LocalHumanVsAiGame game = new LocalHumanVsAiGame();
    private final Button[][] squares = new Button[9][10];
    private final Label turnLabel = new Label();
    private final Label statusLabel = new Label();
    private final ListView<String> moveList = new ListView<>();
    private Position selected;
    private boolean aiThinking;

    HumanVsAiView(Runnable backAction) {
        setPadding(new Insets(18));
        setTop(header(backAction));
        setLeft(informationPanel());
        setCenter(board());
        setRight(recordPanel());
        BorderPane.setMargin(getCenter(), new Insets(16));
        getStylesheets().add(
                HumanVsAiView.class.getResource("/edu/bupt/jieqi/gui/app.css").toExternalForm());
        refresh();
    }

    private HBox header(Runnable backAction) {
        Button back = new Button("返回");
        back.setOnAction(event -> backAction.run());
        Label title = new Label("真人对随机人工智能");
        title.getStyleClass().add("section-title");
        HBox header = new HBox(14, back, title);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox informationPanel() {
        Label red = new Label("红方：玩家");
        Label black = new Label("黑方：随机人工智能");
        Label help = new Label("操作：先选择红方棋子，再点击高亮落点。");
        help.setWrapText(true);
        VBox panel = new VBox(12, red, black, turnLabel, statusLabel, help);
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
                square.setOnAction(event -> handleSquare(position));
                squares[file][rank] = square;
                board.add(square, file, 9 - rank);
            }
        }
        return board;
    }

    private VBox recordPanel() {
        moveList.setCellFactory(list -> wrappingRecordCell());

        Button restart = new Button("重新开始");
        restart.setMaxWidth(Double.MAX_VALUE);
        restart.setOnAction(event -> {
            if (aiThinking) {
                return;
            }
            game.restart();
            selected = null;
            aiThinking = false;
            refresh();
        });

        Button resign = new Button("认输");
        resign.setMaxWidth(Double.MAX_VALUE);
        resign.setOnAction(event -> {
            if (aiThinking) {
                return;
            }
            game.resignHuman();
            selected = null;
            refresh();
        });

        VBox panel = new VBox(10, new Label("走法记录"), moveList, restart, resign);
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

    private void handleSquare(Position position) {
        if (aiThinking
                || game.state().status() != GameStatus.PLAYING
                || game.state().currentTurn() != Color.RED) {
            return;
        }

        Optional<Piece> clickedPiece = game.state().board().pieceAt(position);
        if (selected == null) {
            if (clickedPiece.filter(piece -> piece.owner() == Color.RED).isPresent()) {
                select(position);
            }
            return;
        }

        if (selected.equals(position)) {
            selected = null;
            refresh();
            return;
        }

        Move chosen = legalMovesFromSelected().stream()
                .filter(move -> move.destination().equals(position))
                .findFirst()
                .orElse(null);
        if (chosen != null) {
            game.submitHumanMove(new Move(
                    chosen.source(), chosen.destination(), System.currentTimeMillis()));
            selected = null;
            refresh();
            startAiTurn();
            return;
        }

        if (clickedPiece.filter(piece -> piece.owner() == Color.RED).isPresent()) {
            select(position);
        } else {
            selected = null;
            refresh();
        }
    }

    private void select(Position position) {
        boolean movable = game.legalHumanMoves().stream()
                .anyMatch(move -> move.source().equals(position));
        selected = movable ? position : null;
        refresh();
    }

    private List<Move> legalMovesFromSelected() {
        if (selected == null) {
            return List.of();
        }
        return game.legalHumanMoves().stream()
                .filter(move -> move.source().equals(selected))
                .toList();
    }

    private void startAiTurn() {
        if (game.state().status() != GameStatus.PLAYING
                || game.state().currentTurn() != Color.BLACK) {
            return;
        }
        aiThinking = true;
        refresh();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                game.performAiMove();
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            aiThinking = false;
            refresh();
        });
        task.setOnFailed(event -> {
            aiThinking = false;
            statusLabel.setText("人工智能运行失败：" + task.getException().getMessage());
        });
        Thread thread = new Thread(task, "jieqi-random-ai");
        thread.setDaemon(true);
        thread.start();
    }

    private void refresh() {
        List<Move> selectedMoves = legalMovesFromSelected();
        for (int file = 0; file < 9; file++) {
            for (int rank = 0; rank <= 9; rank++) {
                Position position = new Position(file, rank);
                Button square = squares[file][rank];
                square.getStyleClass().removeAll(
                        "red-piece", "black-piece", "selected-square", "legal-target");
                Piece piece = game.state().board().pieceAt(position).orElse(null);
                square.setText(PieceTextFormatter.format(piece));
                if (piece != null) {
                    square.getStyleClass().add(
                            piece.owner() == Color.RED ? "red-piece" : "black-piece");
                }
                if (position.equals(selected)) {
                    square.getStyleClass().add("selected-square");
                }
                if (selectedMoves.stream().anyMatch(move -> move.destination().equals(position))) {
                    square.getStyleClass().add("legal-target");
                }
            }
        }

        turnLabel.setText(aiThinking
                ? "当前回合：人工智能思考中"
                : "当前回合：" + colorText(game.state().currentTurn()));
        statusLabel.setText(statusText());
        moveList.getItems().setAll(game.moveRecords());
        if (!moveList.getItems().isEmpty()) {
            moveList.scrollTo(moveList.getItems().size() - 1);
        }
    }

    private String colorText(Color color) {
        return color == Color.RED ? "红方" : "黑方";
    }

    private String statusText() {
        return statusText(
                game.state().status(),
                game.isInCheck(Color.RED),
                game.isInCheck(Color.BLACK));
    }

    static String statusText(
            GameStatus status,
            boolean redInCheck,
            boolean blackInCheck) {
        if (status == GameStatus.PLAYING && redInCheck && blackInCheck) {
            return "对局状态：双方均被将军";
        }
        if (status == GameStatus.PLAYING && redInCheck) {
            return "对局状态：红方被将军";
        }
        if (status == GameStatus.PLAYING && blackInCheck) {
            return "对局状态：黑方被将军";
        }
        return switch (status) {
            case WAITING -> "对局状态：等待开始";
            case PLAYING -> "对局状态：进行中";
            case RED_WIN -> "对局结束：红方获胜";
            case BLACK_WIN -> "对局结束：黑方获胜";
            case DRAW -> "对局结束：和棋";
        };
    }
}
