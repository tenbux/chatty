package chatty.util.chatlog;

import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds stream sessions in raw channel log lines using the same rules as the
 * log query script: gaps of silence split sessions, and Chatty's LIVE/OFFLINE
 * markers trim them.
 * <p>
 * One deliberate difference from the script: a group with no LIVE or OFFLINE
 * marker for the channel is offline chat, unless it predates the channel's
 * first marker and has at least 100 chat lines. Logs written before Chatty had
 * markers have none, so there only the amount of chat can tell a stream from
 * offline chat. Later streams can also lack markers: Chatty writes no LIVE
 * marker when it joins a stream that is already live, and none at all while
 * the printStreamLive or logInfo setting is off. Such a stream is dropped
 * unless Chatty saw it go offline.
 */
public final class StreamSessionDetector {

    private static final String TIME = "(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})";
    private static final Pattern ANY_TIMESTAMP = Pattern.compile("^\\[" + TIME + "\\]");
    private static final Pattern CHAT = Pattern.compile("^\\[" + TIME + "\\] <[^>]+>\\*? ");
    private static final Pattern MARKER = Pattern.compile("^\\[" + TIME + "\\] \\*\\* (\\S+) is now (LIVE|OFFLINE) \\*\\*");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            // SMART (the default) would turn 2026-02-30 into Feb 28 and 24:00:00 into the next day.
            .withResolverStyle(ResolverStyle.STRICT);
    private static final long GAP_MS = Duration.ofMinutes(30).toMillis();
    private static final int MIN_CHAT_MESSAGES = 5;
    // Without markers, the amount of chat is the only sign of a stream; offline chat rarely reaches this.
    private static final int MIN_UNMARKED_CHAT_MESSAGES = 100;

    /** Finds the channel's first LIVE or OFFLINE marker in all of its logs. */
    @FunctionalInterface
    public interface FirstMarkerLookup {

        /**
         * @return the naive local time of the first marker, or empty if the
         * logs have none
         * @throws IOException if a log file cannot be read
         */
        Optional<LocalDateTime> find() throws IOException;
    }

    private StreamSessionDetector() {
    }

    /**
     * Detects the ended stream sessions in a window of log lines.
     *
     * @param lines raw log lines of one channel, in file order
     * @param channel channel name including the leading '#'
     * @param zone zone the log timestamps were written in
     * @param beforeMs epoch milliseconds; sessions with any activity at or
     * after this time are discarded
     * @param leftEdgeMs epoch milliseconds of the start of the window the lines
     * were read from; the earliest session is discarded if it begins less than
     * 30 minutes after this time. Long.MIN_VALUE if the window reaches the
     * start of the available history
     * @param firstMarker called at most once, and only if a group without
     * markers may be a stream and the lines do not reach back to the start of
     * the history
     * @return ended ranges (isLive is false) in chronological order
     * @throws IOException if firstMarker cannot read the logs
     */
    public static List<StreamRange> detect(List<String> lines, String channel, ZoneId zone, long beforeMs,
            long leftEdgeMs, FirstMarkerLookup firstMarker) throws IOException {
        // The script works on naive local datetimes, so grouping and trimming use
        // local time as an epoch-style number; across a DST change that differs
        // from real elapsed time, and matching the script is the goal.
        long beforeLocal = toLocalMs(beforeMs, zone);
        long leftEdgeLocal = leftEdgeMs == Long.MIN_VALUE ? Long.MIN_VALUE : toLocalMs(leftEdgeMs, zone);
        String name = channel.startsWith("#") ? channel.substring(1) : channel;
        List<Entry> entries = new ArrayList<>();
        for (String line : lines) {
            Matcher any = ANY_TIMESTAMP.matcher(line);
            if (!any.find()) {
                continue;
            }
            Long timeMs = parseLocalMs(any.group(1));
            if (timeMs == null) {
                continue;
            }
            boolean isChat = CHAT.matcher(line).find();
            entries.add(new Entry(timeMs, isChat, markerOf(line, name)));
        }
        // List.sort is stable, so equal times keep file order as the script does.
        entries.sort(Comparator.comparingLong(Entry::timeMs));

        List<List<Entry>> groups = new ArrayList<>();
        List<Entry> current = null;
        for (Entry entry : entries) {
            if (current == null || entry.timeMs() - current.get(current.size() - 1).timeMs() > GAP_MS) {
                current = new ArrayList<>();
                groups.add(current);
            }
            current.add(entry);
        }

        List<StreamRange> result = new ArrayList<>();
        boolean hasEarlierMarker = false;
        boolean hasLookedUpFirstMarker = false;
        Optional<LocalDateTime> firstMarkerTime = Optional.empty();
        for (int i = 0; i < groups.size(); i++) {
            List<Entry> group = groups.get(i);
            long first = group.get(0).timeMs();
            long last = group.get(group.size() - 1).timeMs();
            boolean hasMarker = group.stream().anyMatch(entry -> entry.marker() != null);
            boolean isAfterMarker = hasEarlierMarker;
            hasEarlierMarker |= hasMarker;
            if (last >= beforeLocal) {
                continue;
            }
            // The Long.MIN_VALUE check runs first so the subtraction below never overflows.
            if (i == 0 && leftEdgeLocal != Long.MIN_VALUE && first - leftEdgeLocal < GAP_MS) {
                continue;
            }
            if (!hasMarker) {
                if (isAfterMarker || countChat(group, first, last) < MIN_UNMARKED_CHAT_MESSAGES) {
                    continue;
                }
                // Lines reaching the start of history already showed there is no earlier marker.
                if (leftEdgeLocal != Long.MIN_VALUE) {
                    if (!hasLookedUpFirstMarker) {
                        firstMarkerTime = firstMarker.find();
                        hasLookedUpFirstMarker = true;
                    }
                    if (firstMarkerTime.isPresent() && last >= toNaiveMs(firstMarkerTime.get())) {
                        continue;
                    }
                }
            }
            StreamRange range = toRange(group, first, last, zone);
            if (range != null) {
                result.add(range);
            }
        }
        return result;
    }

    private static StreamRange toRange(List<Entry> group, long first, long last, ZoneId zone) {
        if (countChat(group, first, last) < MIN_CHAT_MESSAGES) {
            return null;
        }
        long start = first;
        long end = last;
        int liveIndex = -1;
        for (int i = 0; i < group.size(); i++) {
            if (group.get(i).marker() == Marker.LIVE) {
                liveIndex = i;
                break;
            }
        }
        if (liveIndex >= 0) {
            start = group.get(liveIndex).timeMs();
            Entry lastMarker = null;
            for (Entry entry : group) {
                if (entry.marker() != null && entry.timeMs() >= start) {
                    lastMarker = entry;
                }
            }
            // A trailing LIVE means the stream came back or is still up, so the gap end stands.
            if (lastMarker != null && lastMarker.marker() == Marker.OFFLINE) {
                end = lastMarker.timeMs();
            }
            if (countChat(group, start, end) < MIN_CHAT_MESSAGES) {
                return null;
            }
        }
        return new StreamRange(toEpochMs(start, zone), toEpochMs(end, zone), false);
    }

    private static int countChat(List<Entry> group, long from, long to) {
        int count = 0;
        for (Entry entry : group) {
            if (entry.isChat() && entry.timeMs() >= from && entry.timeMs() <= to) {
                count++;
            }
        }
        return count;
    }

    /**
     * @param line a raw log line
     * @param channel channel name including the leading '#'
     * @return whether the line is a LIVE or OFFLINE marker of the channel with
     * a real timestamp
     */
    static boolean isMarkerOf(String line, String channel) {
        return markerOf(line, channel.startsWith("#") ? channel.substring(1) : channel) != null
                && localTimeOf(line) != null;
    }

    private static Marker markerOf(String line, String name) {
        Matcher matcher = MARKER.matcher(line);
        if (!matcher.find() || !matcher.group(2).equalsIgnoreCase(name)) {
            return null;
        }
        return matcher.group(3).equals("LIVE") ? Marker.LIVE : Marker.OFFLINE;
    }

    /**
     * Reads the leading timestamp of a log line with the detector's strict
     * rules, without applying a zone.
     *
     * @param line a raw log line
     * @return the naive local time, or null if the line has no real timestamp
     */
    static LocalDateTime localTimeOf(String line) {
        Matcher matcher = ANY_TIMESTAMP.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDateTime.parse(matcher.group(1), TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            // Matches the digit pattern but is not a real date, e.g. month 13.
            return null;
        }
    }

    private static long toNaiveMs(LocalDateTime time) {
        return time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static Long parseLocalMs(String timestamp) {
        try {
            return toNaiveMs(LocalDateTime.parse(timestamp, TIME_FORMAT));
        } catch (DateTimeParseException ex) {
            // Matches the digit pattern but is not a real date, e.g. month 13.
            return null;
        }
    }

    private static long toLocalMs(long epochMs, ZoneId zone) {
        return Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    // The ambiguous fall-back hour resolves to the earlier offset.
    private static long toEpochMs(long localMs, ZoneId zone) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(localMs), ZoneOffset.UTC).atZone(zone).toInstant().toEpochMilli();
    }

    private enum Marker {
        LIVE, OFFLINE
    }

    private record Entry(long timeMs, boolean isChat, Marker marker) {
    }
}
