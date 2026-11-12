package chatty.util.chatlog;

import chatty.util.chatlog.LeaderboardLogLine.ChatLine;
import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Finds the stream that ended before a given time in a channel's log files and
 * counts who chatted in it.
 */
public class PastStreamFinder {

    /**
     * @param range the stream's time range, never live
     * @param ranking non-bot chatters by message count, highest first
     * @param total number of non-bot messages in the stream
     */
    public record PastStream(StreamRange range, List<ChatterTally.Entry> ranking, int total) {
    }

    // Matches the detector's gap rule, so a stream that began just before the
    // cutoff is still seen as a whole.
    private static final long GAP_MS = Duration.ofMinutes(30).toMillis();
    private static final long INITIAL_WINDOW_MS = Duration.ofDays(7).toMillis();

    private final ChannelLogReader reader;
    private final String channel;
    private final ZoneId zone;

    /**
     * @param reader reads the channel's log files; its scan state is reset by
     * every search
     * @param channel channel name including the leading '#'
     * @param zone zone the log timestamps were written in
     */
    public PastStreamFinder(ChannelLogReader reader, String channel, ZoneId zone) {
        this.reader = reader;
        this.channel = channel;
        this.zone = zone;
    }

    /**
     * Finds the latest stream that ended before the given time.
     *
     * @param beforeMs epoch milliseconds; streams with any activity at or after
     * this time are ignored
     * @return the stream, or empty if the logs hold no earlier stream
     * @throws IOException if a log file cannot be read
     */
    public Optional<PastStream> findBefore(long beforeMs) throws IOException {
        Optional<StreamRange> range = findRangeBefore(beforeMs);
        if (range.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(count(range.get()));
    }

    private Optional<StreamRange> findRangeBefore(long beforeMs) throws IOException {
        long to = beforeMs + GAP_MS;
        OptionalLong oldest = reader.oldestStartMs();
        if (oldest.isEmpty()) {
            List<String> lines = new ArrayList<>();
            reader.scanBetween(Long.MIN_VALUE, to, lines::add);
            return last(StreamSessionDetector.detect(lines, channel, zone, beforeMs, Long.MIN_VALUE));
        }
        long window = INITIAL_WINDOW_MS;
        while (true) {
            long from = beforeMs - window;
            boolean isReachingStart = from <= oldest.getAsLong();
            LocalDateTime fromLocal = toLocal(from);
            LocalDateTime toLocal = toLocal(to);
            List<String> lines = new ArrayList<>();
            reader.scanBetween(isReachingStart ? oldest.getAsLong() : from, to, line -> {
                if (isInRange(line, fromLocal, toLocal)) {
                    lines.add(line);
                }
            });
            List<StreamRange> ranges = StreamSessionDetector.detect(
                    lines, channel, zone, beforeMs, isReachingStart ? Long.MIN_VALUE : from);
            if (!ranges.isEmpty()) {
                return last(ranges);
            }
            if (isReachingStart) {
                return Optional.empty();
            }
            window *= 2;
        }
    }

    private LocalDateTime toLocal(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone);
    }

    private static Optional<StreamRange> last(List<StreamRange> ranges) {
        return ranges.isEmpty() ? Optional.empty() : Optional.of(ranges.get(ranges.size() - 1));
    }

    /**
     * Files are read whole, so lines outside the window must be dropped here or
     * the detector would see a window edge in the middle of the data. The
     * comparison uses naive local time because that is the domain the detector
     * applies its left-edge rule in; comparing instants would misplace lines in
     * the repeated hour of a DST fall-back. Lines without a readable timestamp
     * are kept because the detector ignores them.
     */
    private static boolean isInRange(String line, LocalDateTime from, LocalDateTime to) {
        LocalDateTime time = StreamSessionDetector.localTimeOf(line);
        return time == null || (!time.isBefore(from) && !time.isAfter(to));
    }

    private PastStream count(StreamRange range) throws IOException {
        ChatterTally tally = new ChatterTally();
        reader.scanBetween(range.startMs(), range.endMs(), line -> {
            if (LeaderboardLogLine.parse(line, zone) instanceof ChatLine chat
                    && chat.timeMs() >= range.startMs() && chat.timeMs() <= range.endMs()) {
                tally.add(chat);
            }
        });
        return new PastStream(range, tally.ranking(), tally.total());
    }
}
