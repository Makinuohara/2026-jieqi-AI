package edu.bupt.jieqi.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import edu.bupt.jieqi.protocol.ProtocolCodec;
import java.net.URI;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

final class NetworkGameClient extends WebSocketClient {
    interface Listener {
        void opened();

        void message(JsonObject message);

        void closed(String reason);

        void failed(String message);
    }

    private final ProtocolCodec codec = new ProtocolCodec();
    private final Listener listener;

    NetworkGameClient(URI serverUri, Listener listener) {
        super(serverUri);
        this.listener = listener;
    }

    void sendMessage(JsonObject message) {
        send(codec.toJson(message));
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        listener.opened();
    }

    @Override
    public void onMessage(String text) {
        try {
            listener.message(codec.parse(text));
        } catch (JsonParseException | IllegalStateException exception) {
            listener.failed("无法解析服务器消息：" + exception.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        listener.closed(reason == null || reason.isBlank() ? "连接已关闭" : reason);
    }

    @Override
    public void onError(Exception exception) {
        listener.failed(exception.getMessage());
    }
}
