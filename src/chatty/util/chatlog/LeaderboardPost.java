package chatty.util.chatlog;

import chatty.util.chatlog.ChatterTally.Entry;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats the top of a ranking as chat lines and paces posting them.
 */
public final class LeaderboardPost {

    private static final int MAX_LINES = 3;

    /**
     * Twitch allows 20 messages per 30 seconds to regular chatters, which is one
     * message per 1500 ms.
     */
    private static final long DEFAULT_DELAY_MS = 1500;

    /**
     * Added to the slow mode interval because the server measures it from when
     * it receives a message, so sending exactly on the interval can still be rejected.
     */
    private static final long SLOW_MODE_MARGIN_MS = 1000;

    private LeaderboardPost() {
    }

    /**
     * @param ranking chatters, highest count first
     * @return one line per chatter for the top three, fewer if the ranking is shorter
     */
    public static List<String> formatTop(List<Entry> ranking) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_LINES, ranking.size()); i++) {
            Entry entry = ranking.get(i);
            String noun = entry.count() == 1 ? "message" : "messages";
            lines.add("#" + (i + 1) + " " + entry.name() + ": " + entry.count() + " " + noun);
        }
        return lines;
    }

    /**
     * @param isPrivileged whether the poster is exempt from slow mode and the regular rate limit
     * @param slowModeSecs slow mode interval in seconds, 0 if slow mode is off
     * @return milliseconds to wait between consecutive lines
     */
    public static long delayBetweenLinesMs(boolean isPrivileged, int slowModeSecs) {
        if (isPrivileged) {
            return 0;
        }
        if (slowModeSecs > 0) {
            return slowModeSecs * 1000L + SLOW_MODE_MARGIN_MS;
        }
        return DEFAULT_DELAY_MS;
    }
}
