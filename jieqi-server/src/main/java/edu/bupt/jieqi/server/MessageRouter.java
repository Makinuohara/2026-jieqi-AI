package edu.bupt.jieqi.server;

import com.google.gson.JsonObject;
import edu.bupt.jieqi.protocol.Messages;
import edu.bupt.jieqi.protocol.ProtocolCodec;
import java.util.Objects;
import java.util.Optional;

public final class MessageRouter {
    private final ProtocolCodec codec;

    public MessageRouter(ProtocolCodec codec) {
        this.codec = Objects.requireNonNull(codec);
    }

    public Optional<String> route(String text) {
        JsonObject message = codec.parse(text);
        return switch (codec.messageType(message)) {
            case "ping" -> Optional.of(codec.toJson(
                    new Messages.Pong(message.get("timestamp").getAsLong())));
            case "Login", "register" -> Optional.of(loginResult(message));
            case "startMatch", "cancelMatch", "requestFirstHand", "Ready", "move", "Resign" ->
                    Optional.of(codec.toJson(new Messages.Error(
                            100, "消息入口已建立，房间和联网对局仍待实现")));
            default -> Optional.empty();
        };
    }

    private String loginResult(JsonObject message) {
        String userId = message.has("userId") ? message.get("userId").getAsString() : "guest";
        JsonObject response = new JsonObject();
        response.addProperty("messageType", "loginResult");
        response.addProperty("success", true);
        response.addProperty("message", "临时内存身份");
        response.addProperty("userId", userId);
        return codec.toJson(response);
    }
}
