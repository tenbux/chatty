
package chatty.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Persists a "last known alive" timestamp for the chat connection to a small
 * file on disk, so downtime can be measured across app restarts and unclean
 * shutdowns (crash, force-quit, power loss, network drop), where no
 * "# Log closed" marker exists in the chat log to anchor the gap on.
 *
 * @author tduva
 */
public class ChatUptimeTracker {

    private static final Logger LOGGER = Logger.getLogger(ChatUptimeTracker.class.getName());

    private static final long FLUSH_INTERVAL = 30 * 1000;
    private static final long MIN_LOGGED_DOWNTIME = 5 * 1000;

    private final Path file;
    private volatile long lastFlushed;

    public ChatUptimeTracker(Path file) {
        this.file = file;
        // Without this, the first heartbeat() from a raw IRC line during the connection
        // handshake (which fires before onConnect()'s onRegistered() call) would
        // immediately flush "now" to disk, clobbering the real prior timestamp before
        // onConnect() gets a chance to read and diff against it.
        this.lastFlushed = System.currentTimeMillis();
    }

    /**
     * Call on every received IRC line, to keep the persisted "last alive"
     * timestamp fresh in case of an unclean shutdown. Throttled so this
     * doesn't write to disk on every single line.
     */
    public void heartbeat() {
        long now = System.currentTimeMillis();
        if (now - lastFlushed >= FLUSH_INTERVAL) {
            write(now);
        }
    }

    /**
     * Call on each successful chat connection.
     *
     * @return The downtime in milliseconds since the last known-alive
     * timestamp, or -1 if there is none (first run) or it's below the
     * minimum threshold to be worth reporting
     */
    public long onConnect() {
        long now = System.currentTimeMillis();
        long lastAlive = read();
        write(now);
        if (lastAlive <= 0) {
            return -1;
        }
        long downtime = now - lastAlive;
        return downtime >= MIN_LOGGED_DOWNTIME ? downtime : -1;
    }

    private synchronized void write(long time) {
        try {
            Files.writeString(file, String.valueOf(time), StandardCharsets.UTF_8);
            lastFlushed = time;
        } catch (IOException ex) {
            LOGGER.warning("ChatUptimeTracker: Error saving [" + ex + "]");
        }
    }

    private synchronized long read() {
        try {
            return Long.parseLong(Files.readString(file, StandardCharsets.UTF_8).trim());
        } catch (IOException | NumberFormatException ex) {
            return -1;
        }
    }

}
