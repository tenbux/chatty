
package chatty.util.api.eventsub;

import chatty.util.jws.JWSClient;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Method;
import java.net.URI;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

/**
 * Regression test for the EventSub reconnect bug: after a connection has
 * just replaced another one (session_reconnect handoff) and receives its
 * session_welcome, its "replacedConnection" pointer must be cleared. If it
 * isn't, a later session_welcome on the very same Connection object (as
 * happens when JWSClient auto-reconnects the same object after a plain
 * network drop, not a Twitch-initiated session_reconnect) wrongly takes the
 * "just disconnect the old connection" branch again instead of
 * re-registering EventSub topics, silently killing all EventSub-driven
 * features (mod actions, AutoMod, channel points, shield mode, etc.) for
 * the rest of the session.
 *
 * Drives the real message-parsing and state-machine code (Message.fromJson,
 * Connections.handleMessage2) with a synthetic "session_welcome" payload, no
 * network access. Only "addConnection" is invoked via reflection, since it's
 * private and is the only way to register a Connection without triggering a
 * real WebSocket connect (which only happens in Connection/JWSClient#init).
 */
public class ConnectionsTest {

    private static final URI SERVER = URI.create("wss://test.invalid/");

    private Connections connections;

    @After
    public void cleanUp() {
        if (connections != null) {
            // Never connected, so this is a no-op beyond cancelling the
            // internal Timer; JWSClient#disconnect() swallows any exception
            // from closing a socket that was never opened.
            connections.disconnect();
        }
    }

    @Test
    public void testReplacedConnectionClearedAfterWelcome() throws Exception {
        connections = new Connections(SERVER, new NoOpHandler(), null);

        Connection original = addConnection(connections);
        Connection replacement = addConnection(connections);
        replacement.setReplacedConnection(original);
        assertNotNull("Precondition: replacement connection must start out pointing at the original",
                replacement.getReplacedConnection());

        // Simulate the session_welcome the replacement connection receives
        // right after taking over from "original" (the normal session_reconnect
        // handoff case). No topics on either connection, so this cannot reach
        // registerTopic()/the real Twitch API either way.
        replacement.handleReceived(sessionWelcomeJson("session-after-handoff"));

        assertNull("replacedConnection must be cleared after its session_welcome is handled, "
                        + "otherwise a later reconnect of this same Connection object will keep "
                        + "taking the \"disconnect old connection\" branch instead of "
                        + "re-registering topics",
                replacement.getReplacedConnection());

        // A second welcome on the SAME Connection object simulates JWSClient
        // auto-reconnecting after a plain network drop (not a fresh
        // session_reconnect handoff, so replacedConnection was never re-set).
        // This must not throw, and must leave replacedConnection cleared -
        // i.e. it takes the topic-registration branch, not the "disconnect
        // old connection" branch again.
        replacement.handleReceived(sessionWelcomeJson("session-after-plain-reconnect"));
        assertNull(replacement.getReplacedConnection());
    }

    private static String sessionWelcomeJson(String sessionId) {
        return "{"
                + "\"metadata\":{"
                + "\"message_id\":\"" + sessionId + "-msg\","
                + "\"message_type\":\"session_welcome\","
                + "\"message_timestamp\":\"2023-07-19T14:56:51.634234626Z\""
                + "},"
                + "\"payload\":{"
                + "\"session\":{"
                + "\"id\":\"" + sessionId + "\","
                + "\"status\":\"connected\","
                + "\"connected_at\":\"2023-07-19T14:56:51.616329898Z\","
                + "\"keepalive_timeout_seconds\":10,"
                + "\"reconnect_url\":null"
                + "}"
                + "}"
                + "}";
    }

    /**
     * Calls the private Connections#addConnection(URI), the only way to
     * register a Connection in a Connections instance without going through
     * addTopic()/Connection#init(), which would attempt a real network
     * connection.
     */
    private static Connection addConnection(Connections target) throws Exception {
        Method m = Connections.class.getDeclaredMethod("addConnection", URI.class);
        m.setAccessible(true);
        return (Connection) m.invoke(target, SERVER);
    }

    private static class NoOpHandler implements ConnectionsMessageHandler {

        @Override
        public void handleReceived(int connection, String text, Message message) {
        }

        @Override
        public void handleSent(int connection, String text) {
        }

        @Override
        public void handleConnect(int connection, JWSClient c) {
        }

        @Override
        public void handleDisconnect(int connection) {
        }

        @Override
        public void handleRegisterError(int responseCode) {
        }

    }

}
