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
                            100, "Framework route exists; room/game implementation is pending")));
            default -> Optional.empty();
        };
    }

    private String loginResult(JsonObject message) {
        String userId = message.has("userId") ? message.get("userId").getAsString() : "guest";
        JsonObject response = new JsonObject();
        response.addProperty("messageType", "loginResult");
        response.addProperty("success", true);
        response.addProperty("message", "temporary in-memory identity");
        response.addProperty("userId", userId);
        return codec.toJson(response);
    }
}

