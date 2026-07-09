package edu.bupt.jieqi.server;

import com.google.gson.JsonParseException;
import edu.bupt.jieqi.protocol.FrameTooLargeException;
import edu.bupt.jieqi.protocol.Messages;
import edu.bupt.jieqi.protocol.ProtocolCodec;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public final class JieqiWebSocketServer extends WebSocketServer {
    private final ProtocolCodec codec = new ProtocolCodec();
    private final NetworkGameHub hub = new NetworkGameHub(codec);
    private final Map<WebSocket, NetworkGameHub.Client> clients = new HashMap<>();
    private final Consumer<String> infoLogger;
    private final Consumer<String> errorLogger;

    public JieqiWebSocketServer(InetSocketAddress address) {
        this(address, System.out::println, System.err::println);
    }

    public JieqiWebSocketServer(
            InetSocketAddress address,
            Consumer<String> infoLogger,
            Consumer<String> errorLogger) {
        super(address);
        this.infoLogger = Objects.requireNonNull(infoLogger);
        this.errorLogger = Objects.requireNonNull(errorLogger);
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        NetworkGameHub.Client client = hub.connected(
                connection::send,
                String.valueOf(connection.getRemoteSocketAddress()));
        clients.put(connection, client);
        infoLogger.accept("客户端已连接：" + connection.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        hub.disconnected(clients.remove(connection));
        infoLogger.accept("客户端已断开：" + (reason == null || reason.isBlank() ? "连接关闭" : reason));
    }

    @Override
    public void onMessage(WebSocket connection, String message) {
        try {
            hub.handle(clients.get(connection), message);
        } catch (FrameTooLargeException exception) {
            connection.close(1009, exception.getMessage());
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException exception) {
            connection.send(codec.toJson(new Messages.Error(4001, "Invalid JSON message")));
        }
    }

    @Override
    public void onError(WebSocket connection, Exception exception) {
        errorLogger.accept("WebSocket 错误：" + exception.getMessage());
    }

    @Override
    public void onStart() {
        setConnectionLostTimeout(0);
        infoLogger.accept("揭棋服务器已监听 ws://0.0.0.0:" + getPort());
        infoLogger.accept("当前支持搜索玩家大厅、邀请匹配和基础联网对局。");
    }
}
