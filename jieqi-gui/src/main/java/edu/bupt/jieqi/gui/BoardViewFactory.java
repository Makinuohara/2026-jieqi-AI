package edu.bupt.jieqi.gui;

import edu.bupt.jieqi.model.Position;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.geometry.VPos;

final class BoardViewFactory {
    static final double PIECE_SIZE = 56;

    private static final double GRID_SPACING = 66;
    private static final double BOARD_PADDING = 34;
    private static final double BOARD_WIDTH = BOARD_PADDING * 2 + GRID_SPACING * 8;
    private static final double BOARD_HEIGHT = BOARD_PADDING * 2 + GRID_SPACING * 9;
    private static final double GRID_LEFT = BOARD_PADDING;
    private static final double GRID_TOP = BOARD_PADDING;

    private BoardViewFactory() {
    }

    static StackPane createBoard(Button[][] squares, Consumer<Position> clickHandler, boolean interactive) {
        Canvas boardCanvas = new Canvas(BOARD_WIDTH, BOARD_HEIGHT);
        drawBoard(boardCanvas.getGraphicsContext2D());

        GridPane pieceLayer = new GridPane();
        pieceLayer.setAlignment(Pos.CENTER);
        pieceLayer.setHgap(GRID_SPACING - PIECE_SIZE);
        pieceLayer.setVgap(GRID_SPACING - PIECE_SIZE);
        pieceLayer.setPadding(new Insets(BOARD_PADDING - PIECE_SIZE / 2));
        pieceLayer.getStyleClass().add("board-piece-layer");

        for (int rank = 9; rank >= 0; rank--) {
            for (int file = 0; file < 9; file++) {
                Position position = new Position(file, rank);
                Button square = new Button();
                square.getStyleClass().addAll("square", "empty-square");
                square.setMinSize(PIECE_SIZE, PIECE_SIZE);
                square.setPrefSize(PIECE_SIZE, PIECE_SIZE);
                square.setMaxSize(PIECE_SIZE, PIECE_SIZE);
                square.setFocusTraversable(false);
                square.setMouseTransparent(!interactive);
                if (interactive && clickHandler != null) {
                    square.setOnAction(event -> clickHandler.accept(position));
                }
                squares[file][rank] = square;
                pieceLayer.add(square, file, 9 - rank);
            }
        }

        StackPane board = new StackPane(boardCanvas, pieceLayer);
        board.getStyleClass().add("board-shell");
        board.setMinSize(BOARD_WIDTH, BOARD_HEIGHT);
        board.setPrefSize(BOARD_WIDTH, BOARD_HEIGHT);
        board.setMaxSize(BOARD_WIDTH, BOARD_HEIGHT);
        return board;
    }

    private static void drawBoard(GraphicsContext graphics) {
        graphics.setFill(Color.web("#f8e9c8"));
        graphics.fillRoundRect(0, 0, BOARD_WIDTH, BOARD_HEIGHT, 26, 26);

        graphics.setFill(Color.web("#edd2a2"));
        graphics.fillRoundRect(10, 10, BOARD_WIDTH - 20, BOARD_HEIGHT - 20, 20, 20);

        graphics.setStroke(Color.web("#6e4d26"));
        graphics.setLineWidth(2.2);
        graphics.strokeRoundRect(10, 10, BOARD_WIDTH - 20, BOARD_HEIGHT - 20, 20, 20);

        graphics.setStroke(Color.web("#8a6331"));
        graphics.setLineWidth(1.8);
        for (int file = 0; file < 9; file++) {
            double x = GRID_LEFT + file * GRID_SPACING;
            graphics.strokeLine(x, GRID_TOP, x, GRID_TOP + GRID_SPACING * 4);
            graphics.strokeLine(x, GRID_TOP + GRID_SPACING * 5, x, GRID_TOP + GRID_SPACING * 9);
        }
        for (int rank = 0; rank < 10; rank++) {
            double y = GRID_TOP + rank * GRID_SPACING;
            graphics.strokeLine(GRID_LEFT, y, GRID_LEFT + GRID_SPACING * 8, y);
        }

        drawPalace(graphics, GRID_LEFT + GRID_SPACING * 3, GRID_TOP);
        drawPalace(graphics, GRID_LEFT + GRID_SPACING * 3, GRID_TOP + GRID_SPACING * 7);
        drawRiverText(graphics);
        drawMarkers(graphics);
    }

    private static void drawPalace(GraphicsContext graphics, double left, double top) {
        graphics.strokeLine(left, top, left + GRID_SPACING * 2, top + GRID_SPACING * 2);
        graphics.strokeLine(left + GRID_SPACING * 2, top, left, top + GRID_SPACING * 2);
    }

    private static void drawRiverText(GraphicsContext graphics) {
        double riverTop = GRID_TOP + GRID_SPACING * 4;
        double riverHeight = GRID_SPACING;
        graphics.setFill(Color.web("#efd8ad"));
        graphics.fillRect(GRID_LEFT + 2, riverTop + 2, GRID_SPACING * 8 - 4, riverHeight - 4);

        graphics.setFill(Color.web("#8e6233"));
        graphics.setFont(Font.font("STKaiti", FontWeight.BOLD, 28));
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        graphics.fillText("楚 河", GRID_LEFT + GRID_SPACING * 1.9, riverTop + riverHeight / 2);
        graphics.fillText("汉 界", GRID_LEFT + GRID_SPACING * 6.1, riverTop + riverHeight / 2);
    }

    private static void drawMarkers(GraphicsContext graphics) {
        for (double[] marker : new double[][] {
                {1, 2}, {7, 2}, {0, 3}, {2, 3}, {4, 3}, {6, 3}, {8, 3},
                {1, 7}, {7, 7}, {0, 6}, {2, 6}, {4, 6}, {6, 6}, {8, 6}
        }) {
            drawMarker(graphics, GRID_LEFT + marker[0] * GRID_SPACING, GRID_TOP + marker[1] * GRID_SPACING);
        }
    }

    private static void drawMarker(GraphicsContext graphics, double x, double y) {
        graphics.setStroke(Color.web("#8a6331"));
        graphics.setLineWidth(1.4);
        double gap = 7;
        double arm = 10;
        boolean leftEdge = x == GRID_LEFT;
        boolean rightEdge = x == GRID_LEFT + GRID_SPACING * 8;
        if (!leftEdge) {
            graphics.strokeLine(x - gap, y - gap, x - gap - arm, y - gap);
            graphics.strokeLine(x - gap, y - gap, x - gap, y - gap - arm);
            graphics.strokeLine(x - gap, y + gap, x - gap - arm, y + gap);
            graphics.strokeLine(x - gap, y + gap, x - gap, y + gap + arm);
        }
        if (!rightEdge) {
            graphics.strokeLine(x + gap, y - gap, x + gap + arm, y - gap);
            graphics.strokeLine(x + gap, y - gap, x + gap, y - gap - arm);
            graphics.strokeLine(x + gap, y + gap, x + gap + arm, y + gap);
            graphics.strokeLine(x + gap, y + gap, x + gap, y + gap + arm);
        }
    }
}
