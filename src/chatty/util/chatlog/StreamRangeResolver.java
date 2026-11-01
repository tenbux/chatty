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
     * Largest gap between a stream start and an OFFLINE marker for the two to be
     * treated as one interrupted stream.
     */
    static final long JOIN_GAP = 10 * 60 * 1000;

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
            return new StreamRange(extendBack(sorted, twitch.picnicStartMs()), Long.MAX_VALUE, true);
        }
        if (twitch.picnicStartMs() != UNKNOWN && twitch.lastOnlineMs() != UNKNOWN) {
            return new StreamRange(extendBack(sorted, twitch.picnicStartMs()), twitch.lastOnlineMs(), false);
        }
        if (!sorted.isEmpty()) {
            StatusMarker last = sorted.get(sorted.size() - 1);
            if (!last.isLive()) {
                StatusMarker live = latestLiveBefore(sorted, last.timeMs());
                if (live != null) {
                    return new StreamRange(extendBack(sorted, live.timeMs()), last.timeMs(), false);
                }
            }
        }
        return new Unavailable("No recent stream found in the logs");
    }

    /**
     * Moves a stream start earlier while an OFFLINE marker near it is preceded by
     * a LIVE marker, meaning the stream only dropped briefly. The distance check
     * is absolute because OFFLINE detection can land slightly after the start
     * Twitch reports.
     */
    private static long extendBack(List<StatusMarker> sorted, long startMs) {
        long start = startMs;
        while (true) {
            StatusMarker offline = latestOfflineNear(sorted, start);
            if (offline == null) {
                return start;
            }
            StatusMarker live = latestLiveBefore(sorted, offline.timeMs());
            if (live == null || live.timeMs() >= start) {
                return start;
            }
            start = live.timeMs();
        }
    }

    private static StatusMarker latestOfflineNear(List<StatusMarker> sorted, long timeMs) {
        for (int i = sorted.size() - 1; i >= 0; i--) {
            StatusMarker marker = sorted.get(i);
            if (!marker.isLive() && Math.abs(marker.timeMs() - timeMs) <= JOIN_GAP) {
                return marker;
            }
        }
        return null;
    }

    private static StatusMarker latestLiveBefore(List<StatusMarker> sorted, long timeMs) {
        for (int i = sorted.size() - 1; i >= 0; i--) {
            StatusMarker marker = sorted.get(i);
            if (marker.isLive() && marker.timeMs() < timeMs) {
                return marker;
            }
        }
        return null;
    }
}
