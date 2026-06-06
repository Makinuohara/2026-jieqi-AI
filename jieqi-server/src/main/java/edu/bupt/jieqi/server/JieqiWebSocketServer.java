package edu.bupt.jieqi.server;

import com.google.gson.JsonParseException;
import edu.bupt.jieqi.protocol.FrameTooLargeException;
import edu.bupt.jieqi.protocol.Messages;
import edu.bupt.jieqi.protocol.ProtocolCodec;
import java.net.InetSocketAddress;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public final class JieqiWebSocketServer extends WebSocketServer {
    private final ProtocolCodec codec = new ProtocolCodec();
    private final MessageRouter router = new MessageRouter(codec);

    public JieqiWebSocketServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        System.out.println("Connected: " + connection.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        System.out.println("Disconnected: " + reason);
    }

    @Override
    public void onMessage(WebSocket connection, String message) {
        try {
            router.route(message).ifPresent(connection::send);
        } catch (FrameTooLargeException exception) {
            connection.close(1009, exception.getMessage());
        } catch (JsonParseException | IllegalStateException exception) {
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
        System.out.println("Jieqi server listening at ws://0.0.0.0:" + getPort());
        System.out.println("Framework mode: room and full game routing remain team F tasks.");
    }
}

