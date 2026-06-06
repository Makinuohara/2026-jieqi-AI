package edu.bupt.jieqi.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ProtocolCodec {
    public static final int MAX_FRAME_BYTES = 1024;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public JsonObject parse(String text) {
        Objects.requireNonNull(text, "text");
        int byteLength = text.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength >= MAX_FRAME_BYTES) {
            throw new FrameTooLargeException(byteLength);
        }
        JsonObject object = gson.fromJson(text, JsonObject.class);
        if (object == null || !object.has("messageType") || !object.get("messageType").isJsonPrimitive()) {
            throw new JsonParseException("messageType is required");
        }
        return object;
    }

    public String messageType(JsonObject object) {
        return object.get("messageType").getAsString();
    }

    public String toJson(Object message) {
        String json = gson.toJson(message);
        int byteLength = json.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength >= MAX_FRAME_BYTES) {
            throw new FrameTooLargeException(byteLength);
        }
        return json;
    }
}

