package edu.bupt.jieqi.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class ProtocolCodecTest {
    private final ProtocolCodec codec = new ProtocolCodec();

    @Test
    void readsMessageTypeAndKeepsUnknownFieldsCompatible() {
        JsonObject message = codec.parse("""
                {"messageType":"move","fromX":"a","fromY":0,"toX":"a","toY":1,
                 "isFlip":true,"futureField":"ignored"}
                """);

        assertEquals("move", codec.messageType(message));
        assertEquals("ignored", message.get("futureField").getAsString());
    }

    @Test
    void rejectsFramesAtOrAboveConfiguredLimit() {
        String oversized = "{\"messageType\":\"x\",\"data\":\""
                + "a".repeat(ProtocolCodec.MAX_FRAME_BYTES)
                + "\"}";
        assertThrows(FrameTooLargeException.class, () -> codec.parse(oversized));
    }
}
