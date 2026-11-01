package chatty.util.chatlog;

import chatty.util.chatlog.LeaderboardLogLine.ChatLine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts chat messages per chatter and ranks them. Bots are ignored. Lines may
 * be added in any time order. Not thread-safe.
 */
public class ChatterTally {

    /**
     * One ranked chatter.
     *
     * @param name display name taken from the chatter's latest message
     * @param count number of messages counted for the chatter
     */
    public record Entry(String name, int count) {
    }

    private static class Chatter {

        private final String key;
        private String name;
        private int count;
        private long firstTimeMs;
        private long latestTimeMs;

        Chatter(ChatLine line) {
            this.key = line.key();
            this.name = line.name();
            this.firstTimeMs = line.timeMs();
            this.latestTimeMs = line.timeMs();
        }

        void add(ChatLine line) {
            count++;
            firstTimeMs = Math.min(firstTimeMs, line.timeMs());
            // Backfilled lines can arrive out of order, so insertion order
            // cannot decide which capitalization is the latest.
            if (line.timeMs() >= latestTimeMs) {
                latestTimeMs = line.timeMs();
                name = line.name();
            }
        }
    }

    private static final Comparator<Chatter> RANKING_ORDER = Comparator
            .comparingInt((Chatter chatter) -> chatter.count).reversed()
            .thenComparingLong(chatter -> chatter.firstTimeMs)
            .thenComparing(chatter -> chatter.key);

    private final Map<String, Chatter> chatters = new HashMap<>();
    private int total;

    /**
     * Counts a message toward its chatter. Messages from bots are ignored.
     *
     * @param line the chat message
     */
    public void add(ChatLine line) {
        if (line.isBot()) {
            return;
        }
        Chatter chatter = chatters.computeIfAbsent(line.key(), key -> new Chatter(line));
        chatter.add(line);
        total++;
    }

    /**
     * Ranks chatters by message count, highest first. Ties go to the chatter
     * whose earliest message is older, then to the lower key.
     *
     * @return a new list on every call
     */
    public List<Entry> ranking() {
        List<Chatter> sorted = new ArrayList<>(chatters.values());
        sorted.sort(RANKING_ORDER);
        List<Entry> result = new ArrayList<>(sorted.size());
        for (Chatter chatter : sorted) {
            result.add(new Entry(chatter.name, chatter.count));
        }
        return result;
    }

    /**
     * @return the number of messages from non-bot chatters
     */
    public int total() {
        return total;
    }
}
