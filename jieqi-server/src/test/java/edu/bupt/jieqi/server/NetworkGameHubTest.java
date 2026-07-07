package edu.bupt.jieqi.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import edu.bupt.jieqi.protocol.ProtocolCodec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NetworkGameHubTest {
    private final ProtocolCodec codec = new ProtocolCodec();
    private final NetworkGameHub hub = new NetworkGameHub(codec);

    @Test
    void playersCanEnterLobbyInviteAndStartMatch() {
        List<String> redMessages = new ArrayList<>();
        List<String> blackMessages = new ArrayList<>();
        NetworkGameHub.Client red = hub.connected(redMessages::add, "red");
        NetworkGameHub.Client black = hub.connected(blackMessages::add, "black");

        hub.handle(red, login("红方玩家"));
        hub.handle(black, login("黑方玩家"));
        hub.handle(red, simple("enterLobby"));
        hub.handle(black, simple("enterLobby"));
        hub.handle(red, invite(black.id()));
        hub.handle(black, accept(red.id()));
        hub.handle(red, requestFirstHand(true));
        hub.handle(black, requestFirstHand(false));
        hub.handle(red, simple("Ready"));
        hub.handle(black, simple("Ready"));

        assertTrue(redMessages.stream().anyMatch(message -> message.contains("\"messageType\":\"matchSuccess\"")));
        assertTrue(redMessages.stream().anyMatch(message -> message.contains("\"messageType\":\"gameStart\"")));
        assertTrue(redMessages.stream().anyMatch(message -> message.contains("\"messageType\":\"matchStarted\"")));
        assertTrue(blackMessages.stream().anyMatch(message -> message.contains("\"messageType\":\"matchStarted\"")));
        assertEquals("stateSync", type(lastOfType(redMessages, "stateSync")));
        assertEquals("stateSync", type(lastOfType(blackMessages, "stateSync")));
    }

    @Test
    void serverAppliesMoveAndBroadcastsUpdatedState() {
        List<String> redMessages = new ArrayList<>();
        List<String> blackMessages = new ArrayList<>();
        NetworkGameHub.Client red = hub.connected(redMessages::add, "red");
        NetworkGameHub.Client black = hub.connected(blackMessages::add, "black");
        hub.handle(red, simple("enterLobby"));
        hub.handle(black, simple("enterLobby"));
        hub.handle(red, invite(black.id()));
        hub.handle(black, accept(red.id()));
        hub.handle(red, requestFirstHand(true));
        hub.handle(black, requestFirstHand(false));
        hub.handle(red, simple("Ready"));
        hub.handle(black, simple("Ready"));

        hub.handle(red, move("a0", "a1"));

        JsonObject moveResult = lastOfType(redMessages, "moveResult");
        JsonObject redState = lastOfType(redMessages, "stateSync");
        JsonObject blackState = lastOfType(blackMessages, "stateSync");
        assertEquals(true, moveResult.get("valid").getAsBoolean());
        assertTrue(moveResult.getAsJsonObject("move").has("fromX"));
        assertEquals("BLACK", redState.get("currentTurn").getAsString());
        assertEquals("BLACK", blackState.get("currentTurn").getAsString());
        assertTrue(redState.has("lastRecord"));
        assertTrue(blackState.has("lastRecord"));
    }

    @Test
    void publicStartMatchPairsPlayersAndStartsAfterReady() {
        List<String> firstMessages = new ArrayList<>();
        List<String> secondMessages = new ArrayList<>();
        NetworkGameHub.Client first = hub.connected(firstMessages::add, "first");
        NetworkGameHub.Client second = hub.connected(secondMessages::add, "second");

        hub.handle(first, simple("startMatch"));
        hub.handle(second, simple("startMatch"));
        hub.handle(first, requestFirstHand(true));
        hub.handle(second, requestFirstHand(false));
        hub.handle(first, simple("Ready"));
        hub.handle(second, simple("Ready"));

        assertEquals("matchSuccess", type(lastOfType(firstMessages, "matchSuccess")));
        assertEquals("gameStart", type(lastOfType(firstMessages, "gameStart")));
        assertEquals("stateSync", type(lastOfType(secondMessages, "stateSync")));
    }

    @Test
    void loginAutomaticallyEntersLobbyAndCancelKeepsPlayerVisible() {
        List<String> firstMessages = new ArrayList<>();
        List<String> secondMessages = new ArrayList<>();
        NetworkGameHub.Client first = hub.connected(firstMessages::add, "first");
        NetworkGameHub.Client second = hub.connected(secondMessages::add, "second");

        hub.handle(first, login("甲方玩家"));
        hub.handle(second, login("乙方玩家"));
        hub.handle(first, simple("startMatch"));
        hub.handle(first, simple("cancelMatch"));

        JsonObject lobby = lastOfType(secondMessages, "lobbyPlayers");
        assertEquals(2, lobby.getAsJsonArray("players").size());
        assertTrue(secondMessages.stream().anyMatch(message -> message.contains("\"id\":\"" + first.id() + "\"")));
        assertEquals("matchCanceled", type(lastOfType(firstMessages, "matchCanceled")));
    }

    private String login(String nickname) {
        JsonObject message = simpleObject("Login");
        message.addProperty("nickname", nickname);
        return codec.toJson(message);
    }

    private String invite(String targetId) {
        JsonObject message = simpleObject("invitePlayer");
        message.addProperty("targetId", targetId);
        return codec.toJson(message);
    }

    private String accept(String inviterId) {
        JsonObject message = simpleObject("acceptInvite");
        message.addProperty("inviterId", inviterId);
        return codec.toJson(message);
    }

    private String requestFirstHand(boolean wannaFirst) {
        JsonObject message = simpleObject("requestFirstHand");
        message.addProperty("wannaFirst", wannaFirst);
        return codec.toJson(message);
    }

    private String move(String from, String to) {
        JsonObject message = simpleObject("move");
        message.addProperty("fromX", from.substring(0, 1));
        message.addProperty("fromY", Integer.parseInt(from.substring(1)));
        message.addProperty("toX", to.substring(0, 1));
        message.addProperty("toY", Integer.parseInt(to.substring(1)));
        message.addProperty("isFlip", true);
        return codec.toJson(message);
    }

    private String simple(String type) {
        return codec.toJson(simpleObject(type));
    }

    private JsonObject simpleObject(String type) {
        JsonObject message = new JsonObject();
        message.addProperty("messageType", type);
        return message;
    }

    private JsonObject lastOfType(List<String> messages, String type) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonObject message = codec.parse(messages.get(i));
            if (type.equals(type(message))) {
                return message;
            }
        }
        throw new AssertionError("No message of type " + type + " in " + messages);
    }

    private String type(JsonObject message) {
        return message.get("messageType").getAsString();
    }
}
