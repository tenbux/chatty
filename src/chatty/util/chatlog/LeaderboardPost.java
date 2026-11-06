package chatty.util.chatlog;

import chatty.util.chatlog.ChatterTally.Entry;
import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formats ranking rows as chat lines and paces posting them, and formats the
 * header line for copying them to the clipboard.
 */
public final class LeaderboardPost {

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

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private LeaderboardPost() {
    }

    /**
     * @param rank 1-based position in the ranking
     * @param entry the chatter at that position
     * @return the line for that chatter, as posted to chat
     */
    public static String formatLine(int rank, Entry entry) {
        return "#" + rank + " " + entry.name() + ": " + messages(entry.count());
    }

    /**
     * @param channel channel name including the leading "#"
     * @param range the stream the ranking covers
     * @param total messages counted in that stream
     * @param zone time zone the start date is shown in
     * @param nowMs current time, which a live stream's run time is measured up to
     * @return a line naming the channel, the stream's start date and run time, and the
     *         message total, to put above copied ranking lines
     */
    public static String formatCopyHeader(String channel, StreamRange range, int total, ZoneId zone, long nowMs) {
        // Always dated, since the text is read elsewhere and later, where "today" is unknown.
        String date = DATE_FORMAT.format(Instant.ofEpochMilli(range.startMs()).atZone(zone));
        // A live range has no end yet (its end is Long.MAX_VALUE).
        long endMs = range.isLive() ? nowMs : range.endMs();
        String runTime = formatRunTime(endMs - range.startMs());
        return channel + " stream, " + date + ", " + (range.isLive() ? "live for " + runTime : runTime)
                + ", " + messages(total);
    }

    private static String formatRunTime(long durationMs) {
        long minutes = Math.max(0, durationMs) / 60_000;
        long hours = minutes / 60;
        minutes %= 60;
        if (hours == 0) {
            return minutes + "min";
        }
        return minutes == 0 ? hours + "hr" : hours + "hr " + minutes + "min";
    }

    private static String messages(int count) {
        return String.format(Locale.US, "%,d", count) + (count == 1 ? " message" : " messages");
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
