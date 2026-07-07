package edu.bupt.jieqi.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.bupt.jieqi.model.Board;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.HiddenPiecePool;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.Position;
import edu.bupt.jieqi.protocol.WireBoardCodec;
import edu.bupt.jieqi.rules.GameEngine;
import edu.bupt.jieqi.rules.StandardGameEngine;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

final class NetworkLobbyView extends BorderPane {
    private final GameEngine engine = new StandardGameEngine();
    private final Button[][] squares = new Button[9][10];
    private final TextField serverField = new TextField("ws://127.0.0.1:8887");
    private final TextField nicknameField = new TextField("玩家");
    private final Button connectButton = new Button("连接");
    private final Button quickMatchButton = new Button("快速匹配");
    private final Button cancelMatchButton = new Button("取消匹配");
    private final Button inviteButton = new Button("邀请选中玩家");
    private final Button resignButton = new Button("认输");
    private final CheckBox firstHandBox = new CheckBox("请求先手");
    private final Label connectionLabel = new Label("未连接");
    private final Label roomLabel = new Label("未进入房间");
    private final Label turnLabel = new Label("等待开始");
    private final Label statusLabel = new Label();
    private final ListView<LobbyPlayer> playerList = new ListView<>();
    private final ListView<String> moveList = new ListView<>();
    private final List<String> moveRecords = new ArrayList<>();

    private NetworkGameClient client;
    private GameState state;
    private Color myColor;
    private String myId;
    private String roomId;
    private Move lastMove;
    private Position selected;
    private List<Move> legalMoves = List.of();
    private boolean readySent;
    private Timeline heartbeat;

    NetworkLobbyView(Runnable backAction) {
        setPadding(new Insets(18));
        setTop(header(backAction));
        setLeft(lobbyPanel());
        setCenter(board());
        setRight(recordPanel());
        BorderPane.setMargin(getCenter(), new Insets(16));
        getStylesheets().add(
                NetworkLobbyView.class.getResource("/edu/bupt/jieqi/gui/app.css").toExternalForm());
        refresh();
    }

    void shutdown() {
        closeClient();
    }

    private HBox header(Runnable backAction) {
        Button back = new Button("返回");
        back.setOnAction(event -> {
            backAction.run();
        });
        Label title = new Label("联网对弈");
        title.getStyleClass().add("section-title");
        HBox header = new HBox(14, back, title);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox lobbyPanel() {
        serverField.setMaxWidth(Double.MAX_VALUE);
        nicknameField.setMaxWidth(Double.MAX_VALUE);
        firstHandBox.setSelected(true);

        connectButton.setMaxWidth(Double.MAX_VALUE);
        connectButton.setOnAction(event -> connect());

        quickMatchButton.setMaxWidth(Double.MAX_VALUE);
        quickMatchButton.setOnAction(event -> sendStartMatch());

        cancelMatchButton.setMaxWidth(Double.MAX_VALUE);
        cancelMatchButton.setOnAction(event -> sendSimple("cancelMatch"));

        inviteButton.setMaxWidth(Double.MAX_VALUE);
        inviteButton.setOnAction(event -> inviteSelectedPlayer());

        resignButton.setMaxWidth(Double.MAX_VALUE);
        resignButton.setOnAction(event -> sendSimple("Re" + "sign"));

        playerList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(LobbyPlayer player, boolean empty) {
                super.updateItem(player, empty);
                setText(empty || player == null ? null
                        : player.nickname() + " (" + player.id() + ") - " + player.statusText());
            }
        });
        playerList.setPrefHeight(220);
        playerList.getSelectionModel().selectedItemProperty().addListener((observable, oldPlayer, newPlayer) ->
                refresh());

        VBox panel = new VBox(10,
                new Label("服务器地址"),
                serverField,
                new Label("昵称"),
                nicknameField,
                connectButton,
                firstHandBox,
                quickMatchButton,
                cancelMatchButton,
                new Label("联机房间玩家"),
                playerList,
                inviteButton,
                resignButton,
                connectionLabel,
                roomLabel,
                turnLabel,
                statusLabel);
        panel.getStyleClass().add("panel");
        panel.setPrefWidth(250);
        return panel;
    }

    private StackPane board() {
        return BoardViewFactory.createBoard(squares, this::handleSquareClick, true);
    }

    private VBox recordPanel() {
        moveList.setCellFactory(list -> {
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
        });
        VBox panel = new VBox(10, new Label("走法记录"), moveList);
        panel.getStyleClass().add("panel");
        panel.setPrefWidth(320);
        VBox.setVgrow(moveList, Priority.ALWAYS);
        return panel;
    }

    private void connect() {
        closeClient();
        try {
            URI uri = URI.create(serverField.getText().trim());
            client = new NetworkGameClient(uri, new NetworkGameClient.Listener() {
                @Override
                public void opened() {
                    Platform.runLater(() -> {
                        connectionLabel.setText("已连接：" + uri);
                        sendEnterRoom();
                        startHeartbeat();
                        refresh();
                    });
                }

                @Override
                public void message(JsonObject message) {
                    Platform.runLater(() -> handleServerMessage(message));
                }

                @Override
                public void closed(String reason) {
                    Platform.runLater(() -> {
                        connectionLabel.setText("连接关闭：" + reason);
                        refresh();
                    });
                }

                @Override
                public void failed(String message) {
                    Platform.runLater(() -> {
                        connectionLabel.setText("连接错误：" + message);
                        refresh();
                    });
                }
            });
            connectionLabel.setText("正在连接...");
            client.connect();
        } catch (IllegalArgumentException exception) {
            connectionLabel.setText("服务器地址无效");
        }
        refresh();
    }

    private void closeClient() {
        stopHeartbeat();
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeat = new Timeline(new KeyFrame(Duration.seconds(10), event -> {
            JsonObject ping = message("ping");
            ping.addProperty("timestamp", System.currentTimeMillis());
            send(ping);
        }));
        heartbeat.setCycleCount(Timeline.INDEFINITE);
        heartbeat.playFromStart();
    }

    private void stopHeartbeat() {
        if (heartbeat != null) {
            heartbeat.stop();
            heartbeat = null;
        }
    }

    private void sendEnterRoom() {
        JsonObject message = message("enterLobby");
        message.addProperty("nickname", nicknameField.getText().trim());
        send(message);
    }

    private void sendStartMatch() {
        JsonObject message = message("startMatch");
        send(message);
        statusLabel.setText("正在快速匹配...");
    }

    private void sendFirstHandRequest() {
        JsonObject message = message("requestFirstHand");
        message.addProperty("wannaFirst", firstHandBox.isSelected());
        send(message);
    }

    private void inviteSelectedPlayer() {
        LobbyPlayer selectedPlayer = playerList.getSelectionModel().getSelectedItem();
        if (selectedPlayer == null) {
            statusLabel.setText("请先选择一个玩家");
            return;
        }
        JsonObject message = message("invitePlayer");
        message.addProperty("targetId", selectedPlayer.id());
        send(message);
        statusLabel.setText("已邀请 " + selectedPlayer.nickname());
    }

    private void sendSimple(String type) {
        send(message(type));
    }

    private void send(JsonObject message) {
        if (client == null || !client.isOpen()) {
            statusLabel.setText("请先连接服务器");
            refresh();
            return;
        }
        client.sendMessage(message);
    }

    private JsonObject message(String type) {
        JsonObject message = new JsonObject();
        message.addProperty("messageType", type);
        return message;
    }

    private void handleServerMessage(JsonObject message) {
        String type = message.get("messageType").getAsString();
        switch (type) {
            case "loginResult" -> {
                myId = string(message, "userId");
                connectionLabel.setText("已登录：" + string(message, "nickname", myId));
                statusLabel.setText("已进入联机房间");
            }
            case "enteredLobby" -> statusLabel.setText("已进入联机房间");
            case "leftLobby" -> statusLabel.setText("已离开联机房间");
            case "matchWaiting" -> statusLabel.setText("正在等待随机匹配");
            case "matchCanceled" -> {
                clearFinishedRoom();
                statusLabel.setText("已取消快速匹配");
            }
            case "lobbyPlayers" -> updateLobbyPlayers(message.getAsJsonArray("players"));
            case "inviteSent" -> statusLabel.setText("等待 " + string(message, "targetName") + " 接受邀请");
            case "inviteRejected" -> statusLabel.setText(string(message, "targetName") + " 拒绝了邀请");
            case "matchInvite" -> showInvite(message);
            case "matchSuccess" -> statusLabel.setText("匹配成功，对手：" + string(message, "opponentNickname"));
            case "gameStart" -> handleGameStart(message);
            case "matchStarted" -> handleMatchStarted(message);
            case "stateSync" -> handleStateSync(message);
            case "moveResult" -> statusLabel.setText(string(message, "message", "走法已提交"));
            case "timeout" -> {
                clearFinishedRoom();
                statusLabel.setText("玩家超时，胜者：" + string(message, "winnerId"));
            }
            case "gameOver" -> {
                clearFinishedRoom();
                statusLabel.setText("对局结束：" + string(message, "winner") + " 获胜，原因 "
                        + string(message, "reason"));
            }
            case "pong" -> {
            }
            case "error" -> statusLabel.setText("服务器提示：" + string(message, "message"));
            default -> statusLabel.setText("收到消息：" + type);
        }
        refresh();
    }

    private void clearFinishedRoom() {
        roomId = null;
        myColor = null;
        state = null;
        selected = null;
        lastMove = null;
        readySent = false;
        legalMoves = List.of();
        roomLabel.setText("未进入房间");
    }

    private void updateLobbyPlayers(JsonArray players) {
        List<LobbyPlayer> updated = new ArrayList<>();
        players.forEach(element -> {
            JsonObject player = element.getAsJsonObject();
            String id = string(player, "id");
            if (!id.equals(myId)) {
                updated.add(new LobbyPlayer(
                        id,
                        string(player, "nickname", id),
                        string(player, "status", "idle")));
            }
        });
        playerList.getItems().setAll(updated);
    }

    private void showInvite(JsonObject message) {
        String fromId = string(message, "fromId");
        String fromName = string(message, "fromName", fromId);
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                fromName + " 想与你对弈，是否接受？",
                ButtonType.OK,
                ButtonType.CANCEL);
        alert.setTitle("对局邀请");
        alert.setHeaderText("收到联网对弈邀请");
        alert.showAndWait().ifPresent(button -> {
            JsonObject response = message(button == ButtonType.OK ? "acceptInvite" : "rejectInvite");
            response.addProperty("inviterId", fromId);
            send(response);
        });
    }

    private void handleMatchStarted(JsonObject message) {
        boolean newRoom = !string(message, "roomId").equals(roomId);
        roomId = string(message, "roomId");
        if (newRoom) {
            readySent = false;
        }
        myColor = Color.valueOf(string(message, "color"));
        moveRecords.clear();
        moveList.getItems().clear();
        roomLabel.setText("房间 " + roomId + "，你执" + colorText(myColor)
                + "，对手：" + string(message, "opponentName"));
        statusLabel.setText("匹配成功");
        if (!readySent) {
            readySent = true;
            sendFirstHandRequest();
            sendSimple("Ready");
        }
    }

    private void handleGameStart(JsonObject message) {
        myColor = "red".equals(string(message, "yourColor")) ? Color.RED : Color.BLACK;
        statusLabel.setText("游戏开始，你执" + colorText(myColor));
    }

    private void handleStateSync(JsonObject message) {
        Board board = WireBoardCodec.decodePieces(message.getAsJsonArray("pieces"));
        Color currentTurn = Color.valueOf(string(message, "currentTurn"));
        GameStatus status = GameStatus.valueOf(string(message, "status"));
        state = new GameState(
                board,
                currentTurn,
                0,
                0, 0, null, null,
                0, 0, null, null,
                System.currentTimeMillis(),
                status,
                HiddenPiecePool.standard(),
                HiddenPiecePool.standard());
        if (message.has("yourColor")) {
            myColor = Color.valueOf(string(message, "yourColor"));
        }
        if (message.has("roomId")) {
            roomId = string(message, "roomId");
        }
        lastMove = message.has("lastMove") ? decodeMove(string(message, "lastMove")) : lastMove;
        selected = null;
        if (message.has("lastRecord")) {
            moveRecords.add(string(message, "lastRecord"));
            moveList.getItems().setAll(moveRecords);
            moveList.scrollTo(moveRecords.size() - 1);
        }
        statusLabel.setText(status == GameStatus.PLAYING ? "对局进行中" : HumanVsAiView.statusText(
                status,
                message.has("inCheckRed") && message.get("inCheckRed").getAsBoolean(),
                message.has("inCheckBlack") && message.get("inCheckBlack").getAsBoolean()));
    }

    private void handleSquareClick(Position position) {
        if (state == null || myColor == null || state.status() != GameStatus.PLAYING) {
            return;
        }
        if (state.currentTurn() != myColor) {
            statusLabel.setText("等待对方走棋");
            return;
        }
        legalMoves = engine.legalMoves(state);
        if (selected != null) {
            Move chosen = legalMoves.stream()
                    .filter(move -> move.source().equals(selected))
                    .filter(move -> move.destination().equals(position))
                    .findFirst()
                    .orElse(null);
            if (chosen != null) {
                JsonObject move = message("move");
                move.addProperty("fromX", String.valueOf((char) ('a' + chosen.source().file())));
                move.addProperty("fromY", chosen.source().rank());
                move.addProperty("toX", String.valueOf((char) ('a' + chosen.destination().file())));
                move.addProperty("toY", chosen.destination().rank());
                Piece source = state.board().pieceAt(chosen.source()).orElse(null);
                move.addProperty("isFlip", source != null && !source.visible());
                send(move);
                selected = null;
                statusLabel.setText("已提交走法，等待服务器确认");
                refresh();
                return;
            }
        }
        Piece piece = state.board().pieceAt(position).orElse(null);
        if (piece != null && piece.owner() == myColor
                && legalMoves.stream().anyMatch(move -> move.source().equals(position))) {
            selected = position;
        } else {
            selected = null;
        }
        refresh();
    }

    private void refresh() {
        legalMoves = state != null
                && myColor != null
                && state.status() == GameStatus.PLAYING
                && state.currentTurn() == myColor
                ? engine.legalMoves(state)
                : List.of();
        refreshBoard();
        turnLabel.setText(turnText());
        connectButton.setDisable(client != null && client.isOpen());
        quickMatchButton.setDisable(client == null || !client.isOpen() || roomId != null);
        cancelMatchButton.setDisable(client == null || !client.isOpen() || state != null);
        LobbyPlayer selectedPlayer = playerList.getSelectionModel().getSelectedItem();
        inviteButton.setDisable(client == null || !client.isOpen()
                || selectedPlayer == null || !"idle".equals(selectedPlayer.status()));
        resignButton.setDisable(state == null || state.status() != GameStatus.PLAYING);
    }

    private void refreshBoard() {
        for (int file = 0; file < 9; file++) {
            for (int rank = 0; rank <= 9; rank++) {
                Position position = new Position(file, rank);
                Button square = squares[file][rank];
                square.getStyleClass().removeAll(
                        "red-piece", "black-piece", "occupied-piece", "hidden-piece", "check-target",
                        "selected-square", "legal-target-empty", "legal-target-capture",
                        "last-move-from", "last-move-to", "opponent-move-from", "opponent-move-to");
                Piece piece = state == null ? null : state.board().pieceAt(position).orElse(null);
                square.setText(PieceTextFormatter.format(piece));
                if (piece != null) {
                    square.getStyleClass().add("occupied-piece");
                    square.getStyleClass().add(piece.owner() == Color.RED ? "red-piece" : "black-piece");
                    if (!piece.visible()) {
                        square.getStyleClass().add("hidden-piece");
                    }
                }
                if (selected != null && selected.equals(position)) {
                    square.getStyleClass().add("selected-square");
                }
                if (selected != null && legalMoves.stream()
                        .filter(move -> move.source().equals(selected))
                        .anyMatch(move -> move.destination().equals(position))) {
                    square.getStyleClass().add(piece == null ? "legal-target-empty" : "legal-target-capture");
                }
                if (lastMove != null && lastMove.source().equals(position)) {
                    square.getStyleClass().add("last-move-from");
                }
                if (lastMove != null && lastMove.destination().equals(position)) {
                    square.getStyleClass().add("last-move-to");
                }
            }
        }
    }

    private String turnText() {
        if (state == null) {
            return "等待匹配";
        }
        if (state.status() != GameStatus.PLAYING) {
            return HumanVsAiView.statusText(state.status(), false, false);
        }
        String side = colorText(state.currentTurn());
        return state.currentTurn() == myColor ? "轮到你走棋（" + side + "）" : "等待对方走棋（" + side + "）";
    }

    private Move decodeMove(String encoded) {
        String[] parts = encoded.split("-");
        return new Move(Position.parse(parts[0]), Position.parse(parts[1]), 0);
    }

    private String string(JsonObject object, String name) {
        return string(object, name, "");
    }

    private String string(JsonObject object, String name, String fallback) {
        return object.has(name) ? object.get(name).getAsString() : fallback;
    }

    private String colorText(Color color) {
        return color == Color.RED ? "红方" : "黑方";
    }

    private record LobbyPlayer(String id, String nickname, String status) {
        private String statusText() {
            return switch (status) {
                case "matching" -> "匹配中";
                case "playing" -> "对局中";
                default -> "空闲";
            };
        }
    }
}
