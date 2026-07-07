package edu.bupt.jieqi.server;

import com.google.gson.JsonParseException;
import edu.bupt.jieqi.protocol.FrameTooLargeException;
import edu.bupt.jieqi.protocol.Messages;
import edu.bupt.jieqi.protocol.ProtocolCodec;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public final class JieqiWebSocketServer extends WebSocketServer {
    private final ProtocolCodec codec = new ProtocolCodec();
    private final NetworkGameHub hub = new NetworkGameHub(codec);
    private final Map<WebSocket, NetworkGameHub.Client> clients = new HashMap<>();

    public JieqiWebSocketServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        NetworkGameHub.Client client = hub.connected(
                connection::send,
                String.valueOf(connection.getRemoteSocketAddress()));
        clients.put(connection, client);
        System.out.println("Connected: " + connection.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        hub.disconnected(clients.remove(connection));
        System.out.println("Disconnected: " + reason);
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
        System.err.println("WebSocket error: " + exception.getMessage());
    }

    @Override
    public void onStart() {
        setConnectionLostTimeout(0);
        System.out.println("揭棋服务器已监听 ws://0.0.0.0:" + getPort());
        System.out.println("当前支持搜索玩家大厅、邀请匹配和基础联网对局。");
    }
}
