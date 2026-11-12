package chatty.util.chatlog;

import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StreamSessionDetectorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String CHANNEL = "#testchan";
    private static final long BEFORE = at(23, 59);
    private static final long NO_EDGE = Long.MIN_VALUE;

    private static long at(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 31, hour, minute).atZone(UTC).toInstant().toEpochMilli();
    }

    private static String line(int hour, int minute, String text) {
        return String.format("[2026-10-31 %02d:%02d:00] %s", hour, minute, text);
    }

    private static String chat(int hour, int minute) {
        return line(hour, minute, "<user> hi");
    }

    private static String marker(int hour, int minute, String channel, String state) {
        return line(hour, minute, "** " + channel + " is now " + state + " **");
    }

    /** Adds five chats from the given start, five minutes apart. */
    private static void addFiveChats(List<String> lines, int hour, int minute) {
        for (int i = 0; i < 5; i++) {
            int total = minute + i * 5;
            lines.add(chat(hour + total / 60, total % 60));
        }
    }

    private static List<StreamRange> detect(List<String> lines) {
        return StreamSessionDetector.detect(lines, CHANNEL, UTC, BEFORE, NO_EDGE);
    }

    private static void assertRange(long startMs, long endMs, StreamRange range) {
        assertEquals(startMs, range.startMs());
        assertEquals(endMs, range.endMs());
        assertFalse(range.isLive());
    }

    private static List<String> twoStreams() {
        List<String> lines = new ArrayList<>();
        addFiveChats(lines, 10, 0);
        addFiveChats(lines, 11, 0);
        return lines;
    }

    @Test
    public void test_detect_twoStreamsSplitByGap_twoRanges() {
        List<StreamRange> ranges = detect(twoStreams());
        assertEquals(2, ranges.size());
        assertRange(at(10, 0), at(10, 20), ranges.get(0));
        assertRange(at(11, 0), at(11, 20), ranges.get(1));
    }

    @Test
    public void test_detect_gapExactlyThirtyMinutes_sameGroup() {
        List<String> lines = new ArrayList<>();
        for (int m = 0; m <= 3; m++) {
            lines.add(chat(10, m));
        }
        lines.add(chat(10, 33));
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(10, 33), ranges.get(0));
    }

    @Test
    public void test_detect_fourChats_dropped() {
        List<String> lines = new ArrayList<>();
        for (int m = 0; m < 4; m++) {
            lines.add(chat(10, m));
        }
        for (int m = 4; m < 7; m++) {
            lines.add(line(10, m, "You have joined #testchan"));
        }
        assertTrue(detect(lines).isEmpty());
    }

    @Test
    public void test_detect_infoLinesBridgeGap_oneGroup() {
        List<String> lines = new ArrayList<>();
        lines.add(chat(10, 0));
        lines.add(chat(10, 1));
        lines.add(chat(10, 2));
        lines.add(line(10, 25, "You have joined #testchan"));
        lines.add(line(10, 50, "You have joined #testchan"));
        lines.add(chat(10, 54));
        lines.add(chat(10, 55));
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(10, 55), ranges.get(0));
    }

    @Test
    public void test_detect_liveAndOfflineMarkers_trimmed() {
        List<String> lines = new ArrayList<>();
        for (int m = 0; m <= 10; m += 5) {
            lines.add(chat(10, m));
        }
        lines.add(marker(10, 15, "testchan", "LIVE"));
        for (int m = 20; m <= 60; m += 10) {
            lines.add(chat(10 + m / 60, m % 60));
        }
        lines.add(marker(11, 5, "testchan", "OFFLINE"));
        lines.add(chat(11, 10));
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 15), at(11, 5), ranges.get(0));
    }

    @Test
    public void test_detect_trailingLive_keepsGapEnd() {
        List<String> lines = new ArrayList<>();
        lines.add(marker(10, 0, "testchan", "LIVE"));
        for (int m = 5; m <= 25; m += 5) {
            lines.add(chat(10, m));
        }
        lines.add(marker(10, 40, "testchan", "OFFLINE"));
        lines.add(marker(10, 45, "testchan", "LIVE"));
        lines.add(chat(10, 50));
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(10, 50), ranges.get(0));
    }

    @Test
    public void test_detect_offlineWithoutLive_ignored() {
        List<String> lines = new ArrayList<>();
        addFiveChats(lines, 10, 0);
        lines.add(marker(10, 10, "testchan", "OFFLINE"));
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(10, 20), ranges.get(0));
    }

    private static List<String> trimScenario(String markerChannel) {
        List<String> lines = new ArrayList<>();
        for (int m = 0; m < 30; m += 5) {
            lines.add(chat(10, m));
        }
        lines.add(marker(10, 26, markerChannel, "LIVE"));
        lines.add(chat(10, 27));
        lines.add(marker(10, 28, markerChannel, "OFFLINE"));
        return lines;
    }

    @Test
    public void test_detect_trimmedBelowFive_dropped() {
        assertTrue(detect(trimScenario("testchan")).isEmpty());
    }

    @Test
    public void test_detect_otherChannelMarker_ignored() {
        List<StreamRange> ranges = detect(trimScenario("otherchan"));
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(10, 28), ranges.get(0));
    }

    @Test
    public void test_detect_groupTouchingBefore_discarded() {
        List<String> lines = new ArrayList<>();
        for (int m : new int[] {0, 10, 20, 25, 30}) {
            lines.add(chat(8, m));
        }
        addFiveChats(lines, 10, 0);
        lines.add(chat(10, 40));
        List<StreamRange> ranges = StreamSessionDetector.detect(lines, CHANNEL, UTC, at(10, 30), NO_EDGE);
        assertEquals(1, ranges.size());
        assertRange(at(8, 0), at(8, 30), ranges.get(0));
    }

    @Test
    public void test_detect_leftEdgeCut_firstGroupDiscarded() {
        List<StreamRange> ranges = StreamSessionDetector.detect(twoStreams(), CHANNEL, UTC, BEFORE, at(9, 45));
        assertEquals(1, ranges.size());
        assertRange(at(11, 0), at(11, 20), ranges.get(0));
    }

    @Test
    public void test_detect_leftEdgeFarBeforeFirstGroup_kept() {
        List<StreamRange> ranges = StreamSessionDetector.detect(twoStreams(), CHANNEL, UTC, BEFORE, at(9, 0));
        assertEquals(2, ranges.size());
    }

    @Test
    public void test_detect_badDate_skipped() {
        List<String> lines = twoStreams().subList(0, 5);
        lines = new ArrayList<>(lines);
        lines.add(2, "[2026-13-45 10:00:00] <a> x");
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(10, 20), ranges.get(0));
    }

    @Test
    public void test_detect_leftEdgeExactlyThirtyMinutesBefore_kept() {
        List<StreamRange> ranges = StreamSessionDetector.detect(twoStreams(), CHANNEL, UTC, BEFORE, at(9, 30));
        assertEquals(2, ranges.size());
    }

    @Test
    public void test_detect_impossibleDayAndHour24_skipped() {
        List<String> lines = new ArrayList<>();
        addFiveChats(lines, 10, 0);
        // Lenient parsing would turn these into Feb 28 and Nov 1 00:00, moving boundaries.
        lines.add("[2026-10-31 24:00:00] <a> x");
        lines.add("[2026-02-30 10:10:00] <a> x");
        lines.add("[2026-04-31 10:10:00] <a> x");
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(10, 20), ranges.get(0));
    }

    private static long nyMs(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2026, month, day, hour, minute).atZone(ZoneId.of("America/New_York"))
                .toInstant().toEpochMilli();
    }

    private static String nyChat(String date, String time) {
        return "[" + date + " " + time + ":00] <user> hi";
    }

    @Test
    public void test_detect_fallBackNight_matchesScript() {
        List<String> lines = new ArrayList<>();
        for (String time : new String[] {"01:20", "01:40", "01:55", "01:10", "01:30", "01:50", "02:05", "02:20"}) {
            lines.add(nyChat("2026-11-01", time));
        }
        List<StreamRange> ranges = StreamSessionDetector.detect(lines, CHANNEL, ZoneId.of("America/New_York"),
                nyMs(11, 2, 0, 0), NO_EDGE);
        assertEquals(1, ranges.size());
        assertRange(nyMs(11, 1, 1, 10), nyMs(11, 1, 2, 20), ranges.get(0));
    }

    @Test
    public void test_detect_springForwardNight_matchesScript() {
        List<String> lines = new ArrayList<>();
        for (String time : new String[] {"01:00", "01:15", "01:30", "01:45"}) {
            lines.add(nyChat("2026-03-08", time));
        }
        for (String time : new String[] {"03:05", "03:20", "03:35", "03:50", "04:05"}) {
            lines.add(nyChat("2026-03-08", time));
        }
        List<StreamRange> ranges = StreamSessionDetector.detect(lines, CHANNEL, ZoneId.of("America/New_York"),
                nyMs(3, 9, 0, 0), NO_EDGE);
        assertEquals(1, ranges.size());
        assertRange(nyMs(3, 8, 3, 5), nyMs(3, 8, 4, 5), ranges.get(0));
    }
}
