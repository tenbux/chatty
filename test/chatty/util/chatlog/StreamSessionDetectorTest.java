package chatty.util.chatlog;

import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import java.time.LocalDateTime;
import java.time.ZoneId;
import chatty.util.chatlog.StreamSessionDetector.FirstMarkerLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StreamSessionDetectorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String CHANNEL = "#testchan";
    private static final long BEFORE = at(23, 59);
    private static final long NO_EDGE = Long.MIN_VALUE;
    private static final FirstMarkerLookup NO_MARKERS = Optional::empty;
    private static final FirstMarkerLookup NOT_CALLED = () -> {
        fail("first marker looked up");
        return Optional.empty();
    };

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

    private static String live(int hour, int minute) {
        return marker(hour, minute, "testchan", "LIVE");
    }

    /**
     * Adds a LIVE marker and five chats from the given start, eight minutes
     * apart, so the group is a stream lasting 32 minutes.
     */
    private static void addFiveChats(List<String> lines, int hour, int minute) {
        lines.add(live(hour, minute));
        for (int i = 0; i < 5; i++) {
            int total = minute + i * 8;
            lines.add(chat(hour + total / 60, total % 60));
        }
    }

    /** Adds chats one minute apart from 10:00, so 100 of them stay in one group. */
    private static List<String> unmarkedChats(int count) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            lines.add(chat(10 + i / 60, i % 60));
        }
        return lines;
    }

    private static List<StreamRange> detect(List<String> lines) throws Exception {
        return StreamSessionDetector.detect(lines, CHANNEL, UTC, BEFORE, NO_EDGE, NOT_CALLED);
    }

    private static List<StreamRange> detect(List<String> lines, long leftEdgeMs, FirstMarkerLookup firstMarker)
            throws Exception {
        return StreamSessionDetector.detect(lines, CHANNEL, UTC, BEFORE, leftEdgeMs, firstMarker);
    }

    private static void assertRange(long startMs, long endMs, StreamRange range) {
        assertEquals(startMs, range.startMs());
        assertEquals(endMs, range.endMs());
        assertFalse(range.isLive());
    }

    private static List<String> twoStreams() throws Exception {
        List<String> lines = new ArrayList<>();
        addFiveChats(lines, 10, 0);
        addFiveChats(lines, 12, 0);
        return lines;
    }

    @Test
    public void test_detect_twoStreamsSplitByGap_twoRanges() throws Exception {
        List<StreamRange> ranges = detect(twoStreams());
        assertEquals(2, ranges.size());
        assertRange(at(10, 0), at(10, 32), ranges.get(0));
        assertRange(at(12, 0), at(12, 32), ranges.get(1));
    }

    @Test
    public void test_detect_gapExactlyThirtyMinutes_sameGroup() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(live(10, 0));
        for (int m = 0; m <= 3; m++) {
            lines.add(chat(10, m));
        }
        lines.add(chat(10, 33));
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(10, 33), ranges.get(0));
    }

    @Test
    public void test_detect_fourChats_dropped() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(live(10, 0));
        for (int m = 0; m < 4; m++) {
            lines.add(chat(10, m));
        }
        for (int m = 4; m < 7; m++) {
            lines.add(line(10, m, "You have joined #testchan"));
        }
        assertTrue(detect(lines).isEmpty());
    }

    @Test
    public void test_detect_infoLinesBridgeGap_oneGroup() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(live(10, 0));
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
    public void test_detect_liveAndOfflineMarkers_trimmed() throws Exception {
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
    public void test_detect_trailingLive_keepsGapEnd() throws Exception {
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
    public void test_detect_offlineWithoutLive_keepsGapRange() throws Exception {
        List<String> lines = new ArrayList<>();
        for (int m : new int[] {0, 8, 16, 24, 32}) {
            lines.add(chat(10, m));
        }
        lines.add(marker(10, 10, "testchan", "OFFLINE"));
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(10, 32), ranges.get(0));
    }

    private static List<String> trimScenario(String markerChannel) {
        List<String> lines = new ArrayList<>();
        for (int m = 0; m < 30; m += 5) {
            lines.add(chat(10, m));
        }
        lines.add(marker(10, 26, markerChannel, "LIVE"));
        lines.add(chat(10, 27));
        lines.add(marker(10, 28, markerChannel, "OFFLINE"));
        lines.add(chat(10, 31));
        return lines;
    }

    @Test
    public void test_detect_trimmedBelowFive_dropped() throws Exception {
        assertTrue(detect(trimScenario("testchan")).isEmpty());
    }

    @Test
    public void test_detect_otherChannelMarker_ignored() throws Exception {
        List<String> lines = trimScenario("otherchan");
        lines.add(0, live(10, 0));
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(10, 31), ranges.get(0));
    }

    @Test
    public void test_detect_onlyOtherChannelMarker_dropped() throws Exception {
        List<String> lines = new ArrayList<>();
        for (int m : new int[] {0, 8, 16, 24, 32}) {
            lines.add(chat(10, m));
        }
        // Counted as this channel's, an OFFLINE alone would keep the whole group.
        lines.add(marker(10, 10, "otherchan", "OFFLINE"));
        assertTrue(detect(lines).isEmpty());
    }

    @Test
    public void test_detect_groupTouchingBefore_discarded() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(live(8, 0));
        for (int m : new int[] {0, 10, 20, 25, 30}) {
            lines.add(chat(8, m));
        }
        addFiveChats(lines, 10, 0);
        lines.add(chat(10, 40));
        List<StreamRange> ranges = StreamSessionDetector.detect(lines, CHANNEL, UTC, at(10, 30), NO_EDGE, NOT_CALLED);
        assertEquals(1, ranges.size());
        assertRange(at(8, 0), at(8, 30), ranges.get(0));
    }

    @Test
    public void test_detect_leftEdgeCut_firstGroupDiscarded() throws Exception {
        List<StreamRange> ranges = detect(twoStreams(), at(9, 45), NOT_CALLED);
        assertEquals(1, ranges.size());
        assertRange(at(12, 0), at(12, 32), ranges.get(0));
    }

    @Test
    public void test_detect_leftEdgeFarBeforeFirstGroup_kept() throws Exception {
        List<StreamRange> ranges = detect(twoStreams(), at(9, 0), NOT_CALLED);
        assertEquals(2, ranges.size());
    }

    @Test
    public void test_detect_badDate_skipped() throws Exception {
        List<String> lines = twoStreams().subList(0, 6);
        lines = new ArrayList<>(lines);
        lines.add(2, "[2026-13-45 10:00:00] <a> x");
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(10, 32), ranges.get(0));
    }

    @Test
    public void test_detect_leftEdgeExactlyThirtyMinutesBefore_kept() throws Exception {
        List<StreamRange> ranges = detect(twoStreams(), at(9, 30), NOT_CALLED);
        assertEquals(2, ranges.size());
    }

    @Test
    public void test_detect_hour24Line_notCountedAsNextDay() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(live(23, 35));
        for (int m : new int[] {35, 40, 45, 50}) {
            lines.add(chat(23, m));
        }
        // Lenient parsing reads this as 2026-11-01 00:00, completing a five-chat session.
        lines.add("[2026-10-31 24:00:00] <x> hi");
        long beforeMs = LocalDateTime.of(2026, 11, 2, 0, 0).atZone(UTC).toInstant().toEpochMilli();
        assertTrue(StreamSessionDetector.detect(lines, CHANNEL, UTC, beforeMs, NO_EDGE, NOT_CALLED).isEmpty());
    }

    @Test
    public void test_detect_impossibleDayLine_doesNotBridgeGap() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("[2026-02-28 10:00:00] ** testchan is now LIVE **");
        for (int m : new int[] {0, 10, 20, 30, 35}) {
            lines.add(String.format("[2026-02-28 10:%02d:00] <u> hi", m));
        }
        // Lenient parsing reads this as 2026-02-28 10:45, bridging the 35 minute gap.
        lines.add("[2026-02-30 10:45:00] <x> hi");
        lines.add("[2026-02-28 11:10:00] ** testchan is now LIVE **");
        for (int m : new int[] {10, 20, 30, 40, 50}) {
            lines.add(String.format("[2026-02-28 11:%02d:00] <u> hi", m));
        }
        long beforeMs = LocalDateTime.of(2026, 3, 1, 0, 0).atZone(UTC).toInstant().toEpochMilli();
        List<StreamRange> ranges = StreamSessionDetector.detect(lines, CHANNEL, UTC, beforeMs, NO_EDGE, NOT_CALLED);
        long feb28 = LocalDateTime.of(2026, 2, 28, 0, 0).atZone(UTC).toInstant().toEpochMilli();
        assertEquals(2, ranges.size());
        assertRange(feb28 + at(10, 0) - at(0, 0), feb28 + at(10, 35) - at(0, 0), ranges.get(0));
        assertRange(feb28 + at(11, 10) - at(0, 0), feb28 + at(11, 50) - at(0, 0), ranges.get(1));
    }

    @Test
    public void test_detect_unmarked99Chats_dropped() throws Exception {
        assertTrue(detect(unmarkedChats(99), at(1, 0), NOT_CALLED).isEmpty());
    }

    @Test
    public void test_detect_unmarked100ChatsWithoutAnyMarker_kept() throws Exception {
        List<StreamRange> ranges = detect(unmarkedChats(100), at(1, 0), NO_MARKERS);
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(11, 39), ranges.get(0));
    }

    @Test
    public void test_detect_unmarked100ChatsBeforeFirstMarker_kept() throws Exception {
        LocalDateTime firstMarker = LocalDateTime.of(2026, 10, 31, 11, 40);
        List<StreamRange> ranges = detect(unmarkedChats(100), at(1, 0), () -> Optional.of(firstMarker));
        assertEquals(1, ranges.size());
    }

    @Test
    public void test_detect_unmarked100ChatsAfterFirstMarker_dropped() throws Exception {
        LocalDateTime firstMarker = LocalDateTime.of(2026, 10, 31, 11, 39);
        assertTrue(detect(unmarkedChats(100), at(1, 0), () -> Optional.of(firstMarker)).isEmpty());
    }

    @Test
    public void test_detect_unmarkedAfterMarkerInLines_droppedWithoutLookup() throws Exception {
        List<String> lines = new ArrayList<>();
        addFiveChats(lines, 8, 0);
        lines.addAll(unmarkedChats(100));
        List<StreamRange> ranges = detect(lines, at(1, 0), NOT_CALLED);
        assertEquals(1, ranges.size());
        assertRange(at(8, 0), at(8, 32), ranges.get(0));
    }

    @Test
    public void test_detect_unmarkedAfterEdgeCutMarkedGroup_droppedWithoutLookup() throws Exception {
        List<String> lines = new ArrayList<>();
        addFiveChats(lines, 1, 10);
        lines.addAll(unmarkedChats(100));
        assertTrue(detect(lines, at(1, 0), NOT_CALLED).isEmpty());
    }

    @Test
    public void test_detect_unmarkedWithLinesFromStartOfHistory_keptWithoutLookup() throws Exception {
        List<String> lines = unmarkedChats(100);
        addFiveChats(lines, 13, 0);
        List<StreamRange> ranges = detect(lines);
        assertEquals(2, ranges.size());
        assertRange(at(10, 0), at(11, 39), ranges.get(0));
    }

    @Test
    public void test_detect_firstMarkerLookedUpOnce() throws Exception {
        List<String> lines = unmarkedChats(100);
        for (int i = 0; i < 100; i++) {
            lines.add(chat(13 + i / 60, i % 60));
        }
        int[] calls = {0};
        List<StreamRange> ranges = detect(lines, at(1, 0), () -> {
            calls[0]++;
            return Optional.empty();
        });
        assertEquals(2, ranges.size());
        assertEquals(1, calls[0]);
    }

    @Test
    public void test_detect_shortGroupWithLive_kept() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(marker(10, 0, "testchan", "LIVE"));
        for (int m : new int[] {2, 4, 6, 8, 10}) {
            lines.add(chat(10, m));
        }
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 0), at(10, 10), ranges.get(0));
    }

    @Test
    public void test_detect_markerNameDifferentCase_trimmed() throws Exception {
        List<String> lines = new ArrayList<>();
        for (int m = 0; m <= 10; m += 5) {
            lines.add(chat(10, m));
        }
        lines.add(marker(10, 15, "TestChan", "LIVE"));
        for (int m = 20; m <= 40; m += 5) {
            lines.add(chat(10, m));
        }
        lines.add(marker(10, 45, "TestChan", "OFFLINE"));
        lines.add(chat(10, 50));
        List<StreamRange> ranges = detect(lines);
        assertEquals(1, ranges.size());
        assertRange(at(10, 15), at(10, 45), ranges.get(0));
    }

    private static long nyMs(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2026, month, day, hour, minute).atZone(ZoneId.of("America/New_York"))
                .toInstant().toEpochMilli();
    }

    private static String nyChat(String date, String time) {
        return "[" + date + " " + time + ":00] <user> hi";
    }

    @Test
    public void test_detect_fallBackNight_matchesScript() throws Exception {
        List<String> lines = new ArrayList<>();
        for (String time : new String[] {"01:20", "01:40", "01:55", "01:10", "01:30", "01:50", "02:05", "02:20"}) {
            lines.add(nyChat("2026-11-01", time));
        }
        lines.add("[2026-11-01 01:10:00] ** testchan is now LIVE **");
        List<StreamRange> ranges = StreamSessionDetector.detect(lines, CHANNEL, ZoneId.of("America/New_York"),
                nyMs(11, 2, 0, 0), NO_EDGE, NOT_CALLED);
        assertEquals(1, ranges.size());
        assertRange(nyMs(11, 1, 1, 10), nyMs(11, 1, 2, 20), ranges.get(0));
    }

    @Test
    public void test_detect_springForwardNight_matchesScript() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("[2026-03-08 01:00:00] ** testchan is now LIVE **");
        for (String time : new String[] {"01:00", "01:15", "01:30", "01:45"}) {
            lines.add(nyChat("2026-03-08", time));
        }
        lines.add("[2026-03-08 03:05:00] ** testchan is now LIVE **");
        for (String time : new String[] {"03:05", "03:20", "03:35", "03:50", "04:05"}) {
            lines.add(nyChat("2026-03-08", time));
        }
        List<StreamRange> ranges = StreamSessionDetector.detect(lines, CHANNEL, ZoneId.of("America/New_York"),
                nyMs(3, 9, 0, 0), NO_EDGE, NOT_CALLED);
        assertEquals(1, ranges.size());
        assertRange(nyMs(3, 8, 3, 5), nyMs(3, 8, 4, 5), ranges.get(0));
    }
}
