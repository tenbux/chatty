package chatty.util.chatlog;

import chatty.util.chatlog.LeaderboardLogLine.ChatLine;
import chatty.util.chatlog.LeaderboardLogLine.StatusMarker;
import chatty.util.chatlog.StreamRangeResolver.Resolution;
import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import chatty.util.chatlog.StreamRangeResolver.TwitchStreamState;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Keeps the chatter ranking of the current (or most recent) stream up to date by
 * reading a channel log incrementally.
 *
 * <p>Not thread-safe: call {@link #refresh} from one thread only.
 */
public class StreamLeaderboard {

    /**
     * How far back to look for LIVE/OFFLINE markers. A week covers the gap
     * between streams of any regular schedule while bounding the scan.
     */
    private static final long LOOKBACK_MS = 7L * 24 * 60 * 60 * 1000;

    /**
     * Largest difference between the ends of two ended ranges with the same
     * start that still counts as the same range. Twitch's last-online time is
     * derived from a monotonic clock and shifts by a few milliseconds between
     * reads, which must not trigger a full rebuild on every poll.
     */
    private static final long END_TOLERANCE_MS = 5000;

    /**
     * The leaderboard at one point in time.
     *
     * @param resolution the stream range counted, or why there is none
     * @param ranking chatters, highest count first; empty if there is no range
     * @param total number of messages counted; 0 if there is no range
     */
    public record Snapshot(Resolution resolution, List<ChatterTally.Entry> ranking, int total) {
    }

    private final ChannelLogReader reader;
    private final ZoneId zone;
    private final LongSupplier nowMs;

    /** A set because a rebuild can read the same marker in both of its passes. */
    private final Set<StatusMarker> markers = new LinkedHashSet<>();
    private ChatterTally tally = new ChatterTally();
    private Resolution resolution;
    private StreamRange range;

    /**
     * @param reader reader for the channel's log files
     * @param zone zone the log timestamps were written in
     * @param nowMs clock in epoch milliseconds
     */
    public StreamLeaderboard(ChannelLogReader reader, ZoneId zone, LongSupplier nowMs) {
        this.reader = reader;
        this.zone = zone;
        this.nowMs = nowMs;
    }

    /**
     * Reads what was logged since the last call and returns the updated ranking.
     * Rebuilds from the log if the stream range changed.
     *
     * @param twitch current Twitch stream state
     * @return the current leaderboard
     * @throws IOException if the log cannot be read
     */
    public Snapshot refresh(TwitchStreamState twitch) throws IOException {
        if (resolution == null) {
            rebuild(twitch);
        } else {
            boolean hasReset = reader.readAppended(this::handleLine);
            Resolution latest = StreamRangeResolver.resolve(new ArrayList<>(markers), twitch);
            // After a reset the reader re-emitted lines already counted, so the tally cannot be trusted.
            if (hasReset || !isSameRange(latest, resolution)) {
                rebuild(twitch);
            }
        }
        return new Snapshot(resolution, tally.ranking(), tally.total());
    }

    private static boolean isSameRange(Resolution latest, Resolution previous) {
        if (latest.equals(previous)) {
            return true;
        }
        return latest instanceof StreamRange latestRange && previous instanceof StreamRange previousRange
                && !latestRange.isLive() && !previousRange.isLive() && latestRange.startMs() == previousRange.startMs()
                && Math.abs(latestRange.endMs() - previousRange.endMs()) < END_TOLERANCE_MS;
    }

    private void rebuild(TwitchStreamState twitch) throws IOException {
        // Stays null if a read fails, so the next refresh starts over instead of resuming a partial tally.
        resolution = null;
        markers.clear();
        reader.scanFrom(nowMs.getAsLong() - LOOKBACK_MS, line -> {
            if (LeaderboardLogLine.parse(line, zone) instanceof StatusMarker marker) {
                markers.add(marker);
            }
        });
        Resolution resolved = StreamRangeResolver.resolve(new ArrayList<>(markers), twitch);
        tally = new ChatterTally();
        if (resolved instanceof StreamRange streamRange) {
            range = streamRange;
            // Rescans from the range start, so lines written since the marker pass are counted too.
            reader.scanFrom(streamRange.startMs(), this::handleLine);
        } else {
            range = null;
        }
        resolution = resolved;
    }

    private void handleLine(String line) {
        LeaderboardLogLine parsed = LeaderboardLogLine.parse(line, zone);
        if (parsed instanceof StatusMarker marker) {
            markers.add(marker);
        } else if (parsed instanceof ChatLine chat && range != null
                && chat.timeMs() >= range.startMs() && chat.timeMs() <= range.endMs()) {
            tally.add(chat);
        }
    }
}
