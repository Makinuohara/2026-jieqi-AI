package edu.bupt.jieqi.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.bupt.jieqi.protocol.ProtocolCodec;
import org.junit.jupiter.api.Test;

class MessageRouterTest {
    private final ProtocolCodec codec = new ProtocolCodec();
    private final MessageRouter router = new MessageRouter(codec);

    @Test
    void repliesToPingWithMatchingTimestamp() {
        String response = router.route("""
                {"messageType":"ping","timestamp":12345}
                """).orElseThrow();

        assertEquals("pong", codec.messageType(codec.parse(response)));
        assertEquals(12345, codec.parse(response).get("timestamp").getAsLong());
    }

    @Test
    void ignoresUnknownOptionalMessages() {
        assertTrue(router.route("""
                {"messageType":"futureOptionalMessage","value":1}
                """).isEmpty());
    }
}

