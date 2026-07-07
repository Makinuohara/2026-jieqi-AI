package edu.bupt.jieqi.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import edu.bupt.jieqi.model.Color;
import edu.bupt.jieqi.model.GameState;
import edu.bupt.jieqi.model.GameStatus;
import edu.bupt.jieqi.model.Move;
import edu.bupt.jieqi.model.Piece;
import edu.bupt.jieqi.model.PieceType;
import edu.bupt.jieqi.model.Position;
import edu.bupt.jieqi.protocol.Messages;
import edu.bupt.jieqi.protocol.ProtocolCodec;
import edu.bupt.jieqi.protocol.WireBoardCodec;
import edu.bupt.jieqi.rules.ApplyResult;
import edu.bupt.jieqi.rules.GameEngine;
import edu.bupt.jieqi.rules.GameEvent;
import edu.bupt.jieqi.rules.StandardGameEngine;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class NetworkGameHub {
    private static final long MOVE_THINKING_SECONDS = 60;
    private static final long NETWORK_GRACE_SECONDS = 5;
    private static final long FIRST_HAND_NEGOTIATION_SECONDS = 10;

    private final ProtocolCodec codec;
    private final GameEngine engine;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "jieqi-network-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Client> clients = new HashMap<>();
    private final Set<Client> lobby = new LinkedHashSet<>();
    private final Set<Client> matchQueue = new LinkedHashSet<>();
    private final Map<String, String> pendingInvites = new HashMap<>();
    private final Map<String, Room> rooms = new HashMap<>();
    private int guestSequence = 1;
    private int roomSequence = 1;

    NetworkGameHub(ProtocolCodec codec) {
        this(codec, new StandardGameEngine());
    }

    NetworkGameHub(ProtocolCodec codec, GameEngine engine) {
        this.codec = Objects.requireNonNull(codec);
        this.engine = Objects.requireNonNull(engine);
    }

    synchronized Client connected(Consumer<String> sender, String remoteAddress) {
        String id = "P" + guestSequence++;
        Client client = new Client(id, "玩家" + id, sender, remoteAddress);
        clients.put(id, client);
        lobby.add(client);
        sendLoginResult(client);
        broadcastLobby();
        return client;
    }

    synchronized void disconnected(Client client) {
        if (client == null) {
            return;
        }
        lobby.remove(client);
        matchQueue.remove(client);
        clients.remove(client.id);
        pendingInvites.entrySet().removeIf(entry ->
                entry.getKey().equals(client.id) || entry.getValue().equals(client.id));
        if (client.room != null) {
            handleRoomDisconnect(client);
        }
        broadcastLobby();
    }

    synchronized void handle(Client client, String text) {
        JsonObject message = codec.parse(text);
        String type = codec.messageType(message);
        switch (type) {
            case "ping" -> send(client, pong(message));
            case "Login", "login", "register" -> handleLogin(client, message);
            case "startMatch" -> startMatch(client, message);
            case "cancelMatch" -> cancelMatch(client);
            case "Ready" -> ready(client);
            case "requestFirstHand" -> requestFirstHand(client, message);
            case "enterLobby" -> enterLobby(client, message);
            case "leaveLobby" -> leaveLobby(client);
            case "invitePlayer" -> invitePlayer(client, string(message, "targetId"));
            case "acceptInvite" -> acceptInvite(client, string(message, "inviterId"));
            case "rejectInvite" -> rejectInvite(client, string(message, "inviterId"));
            case "move" -> handleMove(client, message);
            case "Resign", "resign" -> resign(client);
            default -> send(client, error(404, "未知消息类型：" + type));
        }
    }

    private void handleLogin(Client client, JsonObject message) {
        String nickname = message.has("nickname")
                ? message.get("nickname").getAsString()
                : string(message, "userId", client.nickname);
        client.nickname = cleanNickname(nickname, client.id);
        if (client.room == null) {
            lobby.add(client);
        }
        sendLoginResult(client, true, "已进入联机房间");
        broadcastLobby();
    }

    private void enterLobby(Client client, JsonObject message) {
        if (message.has("nickname")) {
            client.nickname = cleanNickname(message.get("nickname").getAsString(), client.id);
        }
        if (client.room != null) {
            send(client, error(409, "已经在对局中"));
            return;
        }
        lobby.add(client);
        send(client, simple("enteredLobby"));
        broadcastLobby();
    }

    private void startMatch(Client client, JsonObject message) {
        if (message.has("nickname")) {
            client.nickname = cleanNickname(message.get("nickname").getAsString(), client.id);
        }
        if (client.room != null) {
            send(client, error(409, "已经在对局中"));
            return;
        }
        lobby.add(client);
        Client opponent = matchQueue.stream()
                .filter(candidate -> candidate != client)
                .filter(candidate -> candidate.room == null)
                .findFirst()
                .orElse(null);
        if (opponent == null) {
            matchQueue.add(client);
            send(client, simple("matchWaiting"));
            broadcastLobby();
            return;
        }
        createRoom(opponent, client);
    }

    private void ready(Client client) {
        if (client.room == null) {
            send(client, error(3001, "房间不存在"));
            return;
        }
        client.ready = true;
        Client opponent = client.room.red == client ? client.room.black : client.room.red;
        JsonObject info = simple("roomInfo");
        info.addProperty("opponentReady", true);
        send(opponent, info);
        tryStartGame(client.room);
    }

    private void requestFirstHand(Client client, JsonObject message) {
        if (client.room == null) {
            send(client, error(3001, "房间不存在"));
            return;
        }
        client.wantsFirst = message.has("wannaFirst") && message.get("wannaFirst").getAsBoolean();
        JsonObject info = simple("roomInfo");
        info.addProperty("firstHandRequestAccepted", true);
        info.addProperty("wannaFirst", client.wantsFirst);
        send(client, info);
        tryStartGame(client.room);
    }

    private void leaveLobby(Client client) {
        lobby.remove(client);
        matchQueue.remove(client);
        pendingInvites.entrySet().removeIf(entry ->
                entry.getKey().equals(client.id) || entry.getValue().equals(client.id));
        send(client, simple("leftLobby"));
        broadcastLobby();
    }

    private void cancelMatch(Client client) {
        matchQueue.remove(client);
        send(client, simple("matchCanceled"));
        broadcastLobby();
    }

    private void invitePlayer(Client inviter, String targetId) {
        Client target = clients.get(targetId);
        if (target == null || !lobby.contains(target) || target.room != null) {
            send(inviter, error(404, "目标玩家不可匹配"));
            broadcastLobby();
            return;
        }
        if (target == inviter) {
            send(inviter, error(400, "不能邀请自己"));
            return;
        }
        lobby.add(inviter);
        matchQueue.remove(inviter);
        matchQueue.remove(target);
        pendingInvites.put(target.id, inviter.id);
        JsonObject sent = simple("inviteSent");
        sent.addProperty("targetId", target.id);
        sent.addProperty("targetName", target.nickname);
        send(inviter, sent);

        JsonObject invite = simple("matchInvite");
        invite.addProperty("fromId", inviter.id);
        invite.addProperty("fromName", inviter.nickname);
        send(target, invite);
    }

    private void acceptInvite(Client target, String inviterId) {
        if (!inviterId.equals(pendingInvites.get(target.id))) {
            send(target, error(404, "邀请已失效"));
            return;
        }
        Client inviter = clients.get(inviterId);
        if (inviter == null || inviter.room != null || target.room != null) {
            pendingInvites.remove(target.id);
            send(target, error(409, "邀请方已不可匹配"));
            return;
        }
        pendingInvites.remove(target.id);
        createRoom(inviter, target);
    }

    private void rejectInvite(Client target, String inviterId) {
        pendingInvites.remove(target.id);
        Client inviter = clients.get(inviterId);
        if (inviter != null) {
            JsonObject rejected = simple("inviteRejected");
            rejected.addProperty("targetId", target.id);
            rejected.addProperty("targetName", target.nickname);
            send(inviter, rejected);
        }
    }

    private void createRoom(Client red, Client black) {
        lobby.add(red);
        lobby.add(black);
        matchQueue.remove(red);
        matchQueue.remove(black);
        String roomId = "R" + roomSequence++;
        Room room = new Room(roomId, red, black);
        rooms.put(roomId, room);
        red.room = room;
        red.color = Color.RED;
        red.ready = false;
        red.wantsFirst = null;
        black.room = room;
        black.color = Color.BLACK;
        black.ready = false;
        black.wantsFirst = null;
        room.firstHandTask = scheduler.schedule(
                () -> firstHandNegotiationExpired(room.id),
                FIRST_HAND_NEGOTIATION_SECONDS,
                TimeUnit.SECONDS);
        sendMatchStarted(red, black);
        sendMatchStarted(black, red);
        broadcastLobby();
    }

    private void handleMove(Client client, JsonObject message) {
        Room room = client.room;
        if (room == null) {
            send(client, error(3001, "尚未进入房间"));
            return;
        }
        if (!room.started) {
            send(client, error(409, "对局尚未开始，请等待双方 Ready"));
            return;
        }
        if (room.state.status() != GameStatus.PLAYING) {
            send(client, error(409, "对局已经结束"));
            return;
        }
        if (room.state.currentTurn() != client.color) {
            send(client, error(2002, "还没有轮到你走棋"));
            return;
        }
        Move move = moveFromMessage(message);
        ApplyResult result = engine.apply(room.state, move);
        if (!result.validation().valid()) {
            JsonObject invalid = simple("moveResult");
            invalid.addProperty("success", false);
            invalid.addProperty("valid", false);
            invalid.add("move", publicMove(move, false));
            invalid.addProperty("message", result.validation().message());
            send(client, invalid);
            send(client, error(2001, result.validation().message()));
            return;
        }
        Piece captured = room.state.board().pieceAt(move.destination()).orElse(null);
        room.state = result.state();
        room.lastMove = move;
        room.lastRecord = recordMove(client.color, move, captured, result);
        JsonObject moveResult = simple("moveResult");
        moveResult.addProperty("success", true);
        moveResult.addProperty("valid", true);
        boolean flipped = revealedType(result) != null;
        moveResult.add("move", publicMove(move, flipped));
        if (flipped) {
            moveResult.addProperty("flipResult", revealedType(result).jsonName());
        }
        if (captured != null && captured.visible()) {
            moveResult.addProperty("capturedType", captured.actualType().jsonName());
        }
        send(room.red, moveResult);
        send(room.black, moveResult);
        sendState(room.red, room, room.lastRecord);
        sendState(room.black, room, room.lastRecord);
        if (room.state.status() != GameStatus.PLAYING) {
            cancelMoveTimeout(room);
            broadcastGameOver(room, "checkmate");
            closeRoom(room);
        } else {
            scheduleMoveTimeout(room);
        }
    }

    private void resign(Client client) {
        Room room = client.room;
        if (room == null || room.state.status() != GameStatus.PLAYING) {
            return;
        }
        Color winner = client.color.opposite();
        cancelMoveTimeout(room);
        room.state = withStatus(room.state, winner == Color.RED ? GameStatus.RED_WIN : GameStatus.BLACK_WIN);
        room.lastRecord = colorText(client.color) + "认输，" + colorText(winner) + "获胜";
        sendState(room.red, room, room.lastRecord);
        sendState(room.black, room, room.lastRecord);
        broadcastGameOver(room, "resign");
        closeRoom(room);
    }

    private void handleRoomDisconnect(Client leaving) {
        Room room = leaving.room;
        cancelFirstHandNegotiation(room);
        cancelMoveTimeout(room);
        Client opponent = room.red == leaving ? room.black : room.red;
        if (opponent != null && clients.containsKey(opponent.id)) {
            opponent.room = null;
            opponent.color = null;
            send(opponent, error(410, "对手已断开连接，对局结束"));
        }
        rooms.remove(room.id);
        leaving.room = null;
        leaving.color = null;
    }

    private void sendMatchStarted(Client client, Client opponent) {
        JsonObject matchSuccess = simple("matchSuccess");
        matchSuccess.addProperty("roomId", client.room.id);
        matchSuccess.addProperty("opponentId", opponent.id);
        matchSuccess.addProperty("opponentNickname", opponent.nickname);
        send(client, matchSuccess);

        JsonObject message = simple("matchStarted");
        message.addProperty("roomId", client.room.id);
        message.addProperty("color", client.color.name());
        message.addProperty("opponentId", opponent.id);
        message.addProperty("opponentName", opponent.nickname);
        send(client, message);
    }

    private void tryStartGame(Room room) {
        if (room.started || !room.red.ready || !room.black.ready || !firstHandReady(room)) {
            return;
        }
        startGame(room);
    }

    private boolean firstHandReady(Room room) {
        if (room.firstHandDeadlineExpired) {
            return true;
        }
        boolean redWants = Boolean.TRUE.equals(room.red.wantsFirst);
        boolean blackWants = Boolean.TRUE.equals(room.black.wantsFirst);
        if (redWants != blackWants) {
            return true;
        }
        return room.red.wantsFirst != null && room.black.wantsFirst != null;
    }

    private void startGame(Room room) {
        cancelFirstHandNegotiation(room);
        assignFirstHand(room);
        room.started = true;
        sendGameStart(room.red, room.black);
        sendGameStart(room.black, room.red);
        sendState(room.red, room, null);
        sendState(room.black, room, null);
        scheduleMoveTimeout(room);
    }

    private void assignFirstHand(Room room) {
        Client first = null;
        boolean redWants = Boolean.TRUE.equals(room.red.wantsFirst);
        boolean blackWants = Boolean.TRUE.equals(room.black.wantsFirst);
        boolean redDeclined = Boolean.FALSE.equals(room.red.wantsFirst);
        boolean blackDeclined = Boolean.FALSE.equals(room.black.wantsFirst);
        if (redWants && !blackWants) {
            first = room.red;
        } else if (blackWants && !redWants) {
            first = room.black;
        } else if (redDeclined && !blackDeclined) {
            first = room.black;
        } else if (blackDeclined && !redDeclined) {
            first = room.red;
        } else {
            first = ThreadLocalRandom.current().nextBoolean() ? room.red : room.black;
        }
        if (first == room.black) {
            Client oldRed = room.red;
            room.red = room.black;
            room.black = oldRed;
        }
        room.red.color = Color.RED;
        room.black.color = Color.BLACK;
    }

    private void firstHandNegotiationExpired(String roomId) {
        synchronized (this) {
            Room room = rooms.get(roomId);
            if (room == null || room.started) {
                return;
            }
            room.firstHandDeadlineExpired = true;
            tryStartGame(room);
        }
    }

    private void sendGameStart(Client client, Client opponent) {
        JsonObject gameStart = simple("gameStart");
        gameStart.addProperty("redPlayerId", client.room.red.id);
        gameStart.addProperty("blackPlayerId", client.room.black.id);
        gameStart.addProperty("yourColor", colorValue(client.color));
        gameStart.addProperty("firstHand", client.color == Color.RED);
        gameStart.add("initialBoard", WireBoardCodec.encodePublicBoard(client.room.state.board()));
        send(client, gameStart);

        JsonObject started = simple("matchStarted");
        started.addProperty("roomId", client.room.id);
        started.addProperty("color", client.color.name());
        started.addProperty("opponentId", opponent.id);
        started.addProperty("opponentName", opponent.nickname);
        send(client, started);
    }

    private void sendState(Client client, Room room, String lastRecord) {
        JsonObject message = simple("stateSync");
        message.addProperty("roomId", room.id);
        message.addProperty("yourColor", client.color.name());
        message.addProperty("currentTurn", room.state.currentTurn().name());
        message.addProperty("status", room.state.status().name());
        message.addProperty("inCheckRed", engine.isInCheck(room.state, Color.RED));
        message.addProperty("inCheckBlack", engine.isInCheck(room.state, Color.BLACK));
        if (room.lastMove != null) {
            message.addProperty("lastMove", encodeMove(room.lastMove));
        }
        if (lastRecord != null && !lastRecord.isBlank()) {
            message.addProperty("lastRecord", lastRecord);
        }
        message.add("pieces", WireBoardCodec.encodePieces(room.state.board()));
        send(client, message);
    }

    private void broadcastLobby() {
        JsonArray players = new JsonArray();
        for (Client client : lobby) {
            JsonObject player = new JsonObject();
            player.addProperty("id", client.id);
            player.addProperty("nickname", client.nickname);
            player.addProperty("status", client.room != null ? "playing"
                    : matchQueue.contains(client) ? "matching" : "idle");
            if (client.room != null) {
                player.addProperty("roomId", client.room.id);
            }
            players.add(player);
        }
        for (Client client : clients.values()) {
            JsonObject message = simple("lobbyPlayers");
            message.add("players", players);
            send(client, message);
        }
    }

    private String recordMove(Color mover, Move move, Piece captured, ApplyResult result) {
        StringBuilder record = new StringBuilder(colorText(mover))
                .append(" ")
                .append(move.source())
                .append(" -> ")
                .append(move.destination());
        if (captured != null) {
            record.append("，吃子");
        }
        for (GameEvent event : result.events()) {
            if (event instanceof GameEvent.PieceRevealed revealed) {
                record.append("，翻出").append(pieceText(revealed.type(), mover));
            }
            if (event instanceof GameEvent.GameEnded ended) {
                record.append("，").append(statusText(ended.status()));
            }
        }
        return record.toString();
    }

    private Move moveFromMessage(JsonObject message) {
        if (message.has("from") && message.has("to")) {
            return new Move(Position.parse(string(message, "from")), Position.parse(string(message, "to")), 0);
        }
        String from = string(message, "fromX") + message.get("fromY").getAsInt();
        String to = string(message, "toX") + message.get("toY").getAsInt();
        return new Move(Position.parse(from), Position.parse(to), 0);
    }

    private JsonObject publicMove(Move move, boolean flipped) {
        JsonObject object = new JsonObject();
        object.addProperty("fromX", String.valueOf((char) ('a' + move.source().file())));
        object.addProperty("fromY", move.source().rank());
        object.addProperty("toX", String.valueOf((char) ('a' + move.destination().file())));
        object.addProperty("toY", move.destination().rank());
        object.addProperty("isFlip", flipped);
        return object;
    }

    private PieceType revealedType(ApplyResult result) {
        for (GameEvent event : result.events()) {
            if (event instanceof GameEvent.PieceRevealed revealed) {
                return revealed.type();
            }
        }
        return null;
    }

    private void broadcastGameOver(Room room, String reason) {
        JsonObject gameOver = simple("gameOver");
        Color winner = room.state.status() == GameStatus.RED_WIN
                ? Color.RED
                : room.state.status() == GameStatus.BLACK_WIN ? Color.BLACK : null;
        gameOver.addProperty("winner", winner == null ? "draw" : colorValue(winner));
        gameOver.addProperty("reason", reason);
        gameOver.addProperty("winnerId", winner == null ? "" : winner == Color.RED ? room.red.id : room.black.id);
        send(room.red, gameOver);
        send(room.black, gameOver);
    }

    private void scheduleMoveTimeout(Room room) {
        cancelMoveTimeout(room);
        room.timeoutTask = scheduler.schedule(
                () -> timeoutRoom(room.id),
                MOVE_THINKING_SECONDS + NETWORK_GRACE_SECONDS,
                TimeUnit.SECONDS);
    }

    private void cancelMoveTimeout(Room room) {
        if (room.timeoutTask != null) {
            room.timeoutTask.cancel(false);
            room.timeoutTask = null;
        }
    }

    private void cancelFirstHandNegotiation(Room room) {
        if (room.firstHandTask != null) {
            room.firstHandTask.cancel(false);
            room.firstHandTask = null;
        }
    }

    private void timeoutRoom(String roomId) {
        synchronized (this) {
            Room room = rooms.get(roomId);
            if (room == null || room.state.status() != GameStatus.PLAYING) {
                return;
            }
            Color loser = room.state.currentTurn();
            Color winner = loser.opposite();
            room.state = withStatus(room.state, winner == Color.RED ? GameStatus.RED_WIN : GameStatus.BLACK_WIN);
            room.lastRecord = colorText(loser) + "超时，" + colorText(winner) + "获胜";
            JsonObject timeout = simple("timeout");
            timeout.addProperty("loserId", loser == Color.RED ? room.red.id : room.black.id);
            timeout.addProperty("winnerId", winner == Color.RED ? room.red.id : room.black.id);
            timeout.addProperty("reason", "timeout");
            send(room.red, timeout);
            send(room.black, timeout);
            sendState(room.red, room, room.lastRecord);
            sendState(room.black, room, room.lastRecord);
            closeRoom(room);
        }
    }

    private void closeRoom(Room room) {
        cancelFirstHandNegotiation(room);
        cancelMoveTimeout(room);
        rooms.remove(room.id);
        clearRoomState(room.red);
        clearRoomState(room.black);
        broadcastLobby();
    }

    private void clearRoomState(Client client) {
        client.room = null;
        client.color = null;
        client.ready = false;
        client.wantsFirst = null;
    }

    private String cleanNickname(String nickname, String fallback) {
        if (nickname == null || nickname.isBlank()) {
            return "玩家" + fallback;
        }
        String trimmed = nickname.trim();
        return trimmed.length() > 16 ? trimmed.substring(0, 16) : trimmed;
    }

    private JsonObject pong(JsonObject message) {
        return codec.parse(codec.toJson(new Messages.Pong(message.get("timestamp").getAsLong())));
    }

    private JsonObject simple(String type) {
        JsonObject message = new JsonObject();
        message.addProperty("messageType", type);
        return message;
    }

    private JsonObject error(int code, String text) {
        JsonObject message = simple("error");
        message.addProperty("code", code);
        message.addProperty("message", text);
        return message;
    }

    private void sendLoginResult(Client client) {
        sendLoginResult(client, true, "已进入联机房间");
    }

    private void sendLoginResult(Client client, boolean success, String text) {
        JsonObject response = simple("loginResult");
        response.addProperty("success", success);
        response.addProperty("message", text);
        response.addProperty("userId", client.id);
        response.addProperty("nickname", client.nickname);
        send(client, response);
    }

    private void send(Client client, JsonObject message) {
        client.sender.accept(codec.toJson(message));
    }

    private String string(JsonObject object, String name) {
        return string(object, name, "");
    }

    private String string(JsonObject object, String name, String fallback) {
        return object.has(name) ? object.get(name).getAsString() : fallback;
    }

    private String encodeMove(Move move) {
        return move.source() + "-" + move.destination();
    }

    private GameState withStatus(GameState state, GameStatus status) {
        return new GameState(
                state.board(),
                state.currentTurn(),
                state.noCaptureHalfMoves(),
                state.redConsecutiveCheckCount(),
                state.redConsecutiveChaseCount(),
                state.redChasedPosition(),
                state.redChasedPieceType(),
                state.blackConsecutiveCheckCount(),
                state.blackConsecutiveChaseCount(),
                state.blackChasedPosition(),
                state.blackChasedPieceType(),
                state.turnStartedAt(),
                status,
                state.redHiddenPool(),
                state.blackHiddenPool());
    }

    private String colorText(Color color) {
        return color == Color.RED ? "红方" : "黑方";
    }

    private String colorValue(Color color) {
        return color == Color.RED ? "red" : "black";
    }

    private String statusText(GameStatus status) {
        return switch (status) {
            case RED_WIN -> "红方获胜";
            case BLACK_WIN -> "黑方获胜";
            case DRAW -> "和棋";
            case WAITING, PLAYING -> "继续对局";
        };
    }

    private String pieceText(PieceType type, Color color) {
        return switch (type) {
            case KING -> color == Color.RED ? "帅" : "将";
            case GUARD -> color == Color.RED ? "仕" : "士";
            case BISHOP -> color == Color.RED ? "相" : "象";
            case ROOK -> "车";
            case KNIGHT -> "马";
            case CANNON -> "炮";
            case PAWN -> color == Color.RED ? "兵" : "卒";
        };
    }

    static final class Client {
        private String id;
        private final Consumer<String> sender;
        private final String remoteAddress;
        private String nickname;
        private Room room;
        private Color color;
        private boolean ready;
        private Boolean wantsFirst;

        private Client(String id, String nickname, Consumer<String> sender, String remoteAddress) {
            this.id = id;
            this.nickname = nickname;
            this.sender = sender;
            this.remoteAddress = remoteAddress;
        }

        String id() {
            return id;
        }

        String remoteAddress() {
            return remoteAddress;
        }
    }

    private static final class Room {
        private final String id;
        private Client red;
        private Client black;
        private GameState state = GameState.initial();
        private Move lastMove;
        private String lastRecord;
        private boolean started;
        private boolean firstHandDeadlineExpired;
        private ScheduledFuture<?> firstHandTask;
        private ScheduledFuture<?> timeoutTask;

        private Room(String id, Client red, Client black) {
            this.id = id;
            this.red = red;
            this.black = black;
        }
    }
}
