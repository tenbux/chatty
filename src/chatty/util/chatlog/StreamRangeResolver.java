package chatty.util.chatlog;

import chatty.util.chatlog.LeaderboardLogLine.StatusMarker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides which time range counts as "this stream" from Twitch's stream state
 * and the LIVE/OFFLINE markers found in the channel log.
 */
public final class StreamRangeResolver {

    /**
     * Largest gap between a stream start and an earlier OFFLINE marker for the
     * two to be treated as one interrupted stream.
     */
    static final long JOIN_GAP = 10 * 60 * 1000;

    /**
     * How far after Twitch's reported start an OFFLINE marker may still belong
     * to the drop before it: the marker is written when a stream-info poll sees
     * the change, so it can trail the restart by about one poll.
     */
    static final long OFFLINE_LAG = 3 * 60 * 1000;

    private static final long UNKNOWN = -1;

    private StreamRangeResolver() {
    }

    /**
     * Stream state as reported by Twitch for the current session.
     *
     * @param isLive whether the channel is live
     * @param picnicStartMs epoch milliseconds the current stream started, or -1 if unknown
     * @param lastOnlineMs epoch milliseconds the last stream ended, or -1 if unknown
     */
    public record TwitchStreamState(boolean isLive, long picnicStartMs, long lastOnlineMs) {
    }

    public sealed interface Resolution permits StreamRange, Unavailable {
    }

    /**
     * @param startMs epoch milliseconds the stream started
     * @param endMs epoch milliseconds the stream ended, or Long.MAX_VALUE while live
     * @param isLive whether the stream is still running
     */
    public record StreamRange(long startMs, long endMs, boolean isLive) implements Resolution {
    }

    /**
     * @param reason user-facing explanation of why no range could be determined
     */
    public record Unavailable(String reason) implements Resolution {
    }

    /**
     * Resolves the range of the stream to analyze.
     *
     * @param markers status markers from the log, in any order
     * @param twitch current Twitch stream state
     * @return the stream range, or why none could be determined
     */
    public static Resolution resolve(List<StatusMarker> markers, TwitchStreamState twitch) {
        List<StatusMarker> sorted = new ArrayList<>(markers);
        sorted.sort(Comparator.comparingLong(StatusMarker::timeMs));

        if (twitch.isLive()) {
            if (twitch.picnicStartMs() == UNKNOWN) {
                return new Unavailable("Stream start unknown");
            }
            return new StreamRange(extendBack(sorted, twitch.picnicStartMs(), sorted.size()), Long.MAX_VALUE, true);
        }
        if (twitch.picnicStartMs() != UNKNOWN && twitch.lastOnlineMs() != UNKNOWN) {
            long start = extendBack(sorted, twitch.picnicStartMs(), sorted.size());
            return new StreamRange(start, endAfterLastOnline(sorted, twitch.lastOnlineMs()), false);
        }
        int lastIndex = sorted.size() - 1;
        if (lastIndex >= 1 && !sorted.get(lastIndex).isLive() && sorted.get(lastIndex - 1).isLive()) {
            long start = extendBack(sorted, sorted.get(lastIndex - 1).timeMs(), lastIndex - 1);
            return new StreamRange(start, sorted.get(lastIndex).timeMs(), false);
        }
        return new Unavailable("No recent stream found in the logs");
    }

    /**
     * Prefers the OFFLINE marker written after the last-online time, so the
     * final chat before the offline poll counts and the end matches what the
     * logs alone give after a restart. The last-online time also drifts by a
     * few milliseconds between reads, which a marker time does not.
     */
    private static long endAfterLastOnline(List<StatusMarker> sorted, long lastOnlineMs) {
        for (StatusMarker marker : sorted) {
            if (!marker.isLive() && marker.timeMs() >= lastOnlineMs && marker.timeMs() <= lastOnlineMs + JOIN_GAP) {
                return marker.timeMs();
            }
        }
        return lastOnlineMs;
    }

    /**
     * Moves a stream start earlier while an OFFLINE marker within
     * [start - JOIN_GAP, start + OFFLINE_LAG] directly follows a LIVE marker,
     * meaning the stream only dropped briefly. A marker in between, such as the
     * OFFLINE of an earlier stream, means the LIVE marker began a different
     * stream.
     *
     * @param candidateEnd index in {@code sorted} before which OFFLINE markers are
     * considered; once the start is a LIVE marker, later OFFLINE markers belong
     * to the segment that marker began
     */
    private static long extendBack(List<StatusMarker> sorted, long startMs, int candidateEnd) {
        long start = startMs;
        int end = candidateEnd;
        while (true) {
            int offline = latestOfflineNear(sorted, start, end);
            if (offline <= 0) {
                return start;
            }
            StatusMarker previous = sorted.get(offline - 1);
            if (!previous.isLive() || previous.timeMs() >= start) {
                return start;
            }
            start = previous.timeMs();
            end = offline - 1;
        }
    }

    /** Returns the index of the latest OFFLINE marker near the start before {@code end}, or -1. */
    private static int latestOfflineNear(List<StatusMarker> sorted, long startMs, int end) {
        for (int i = end - 1; i >= 0; i--) {
            StatusMarker marker = sorted.get(i);
            if (!marker.isLive() && marker.timeMs() >= startMs - JOIN_GAP
                    && marker.timeMs() <= startMs + OFFLINE_LAG) {
                return i;
            }
        }
        return -1;
    }
}
