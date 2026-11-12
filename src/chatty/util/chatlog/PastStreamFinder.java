package chatty.util.chatlog;

import chatty.util.chatlog.LeaderboardLogLine.ChatLine;
import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern TIMESTAMP = Pattern.compile("^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\]");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
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
            List<String> lines = new ArrayList<>();
            reader.scanBetween(isReachingStart ? oldest.getAsLong() : from, to, line -> {
                if (isInRange(line, from, to)) {
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

    private static Optional<StreamRange> last(List<StreamRange> ranges) {
        return ranges.isEmpty() ? Optional.empty() : Optional.of(ranges.get(ranges.size() - 1));
    }

    /**
     * Files are read whole, so lines outside the window must be dropped here or
     * the detector would see a window edge in the middle of the data. Lines
     * without a readable timestamp are kept because the detector ignores them.
     */
    private boolean isInRange(String line, long fromMs, long toMs) {
        Matcher matcher = TIMESTAMP.matcher(line);
        if (!matcher.find()) {
            return true;
        }
        long timeMs;
        try {
            timeMs = LocalDateTime.parse(matcher.group(1), TIME_FORMAT).atZone(zone).toInstant().toEpochMilli();
        } catch (DateTimeParseException ex) {
            return true;
        }
        return timeMs >= fromMs && timeMs <= toMs;
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
