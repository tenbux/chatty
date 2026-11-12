package chatty.util.chatlog;

import chatty.util.chatlog.PastStreamFinder.PastStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PastStreamFinderTest {

    private static final String CHANNEL = "#testchan";
    private static final ZoneId ZONE = ZoneId.of("UTC");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path dir;

    @Before
    public void setUp() {
        dir = folder.getRoot().toPath();
    }

    private static long at(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2026, month, day, hour, minute).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static String stamp(int month, int day, int hour, int minute) {
        return String.format("[2026-%02d-%02d %02d:%02d:00]", month, day, hour, minute);
    }

    private static String chat(int month, int day, int hour, int minute, String user) {
        return stamp(month, day, hour, minute) + " <" + user + "> hi\n";
    }

    private static String live(int month, int day, int hour, int minute) {
        return stamp(month, day, hour, minute) + " ** testchan is now LIVE **\n";
    }

    private static String offline(int month, int day, int hour, int minute) {
        return stamp(month, day, hour, minute) + " ** testchan is now OFFLINE **\n";
    }

    private void writeDay(int month, int day, String content) throws IOException {
        Files.writeString(dir.resolve(String.format("2026-%02d-%02d_#testchan.log", month, day)),
                content, StandardCharsets.UTF_8);
    }

    private Optional<PastStream> findBefore(String logSplit, long beforeMs) throws IOException {
        ChannelLogReader reader = new ChannelLogReader(dir, CHANNEL, logSplit, ZONE);
        return new PastStreamFinder(reader, CHANNEL, ZONE).findBefore(beforeMs);
    }

    /** A 20:00 to 22:00 stream with chat every 20 minutes. */
    private static String evening(int month, int day) {
        StringBuilder sb = new StringBuilder(live(month, day, 20, 0));
        for (int minute = 0; minute <= 120; minute += 20) {
            sb.append(chat(month, day, 20 + minute / 60, minute % 60, "alice"));
        }
        sb.append(offline(month, day, 22, 0));
        return sb.toString();
    }

    @Test
    public void test_findBefore_previousStream_rangeAndCounts() throws IOException {
        StringBuilder previous = new StringBuilder(live(10, 30, 20, 0));
        for (int i = 0; i < 3; i++) {
            previous.append(chat(10, 30, 20, 10 + i, "alice"));
        }
        for (int i = 0; i < 2; i++) {
            previous.append(chat(10, 30, 20, 35 + i, "bob"));
        }
        // Spaced under the 30 minute gap so the stream stays one session.
        for (int minute : new int[]{50, 75, 100, 110}) {
            previous.append(chat(10, 30, 20 + minute / 60, minute % 60, "nightbot"));
        }
        previous.append(offline(10, 30, 22, 0));
        writeDay(10, 30, previous.toString());
        writeDay(10, 31, live(10, 31, 20, 0) + chat(10, 31, 20, 1, "alice"));

        PastStream found = findBefore("daily", at(10, 31, 20, 0)).orElseThrow();

        assertEquals(at(10, 30, 20, 0), found.range().startMs());
        assertEquals(at(10, 30, 22, 0), found.range().endMs());
        assertFalse(found.range().isLive());
        assertEquals(List.of(new ChatterTally.Entry("alice", 3), new ChatterTally.Entry("bob", 2)),
                found.ranking());
        assertEquals(5, found.total());
    }

    @Test
    public void test_findBefore_streamAcrossMidnight_countedWhole() throws IOException {
        StringBuilder first = new StringBuilder(live(10, 29, 23, 30));
        for (int i = 0; i < 3; i++) {
            first.append(chat(10, 29, 23, 40 + i, "alice"));
        }
        StringBuilder second = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            second.append(chat(10, 30, 0, 10 + i, "bob"));
        }
        second.append(offline(10, 30, 0, 30));
        writeDay(10, 29, first.toString());
        writeDay(10, 30, second.toString());
        writeDay(10, 31, live(10, 31, 20, 0) + chat(10, 31, 20, 1, "alice"));

        PastStream found = findBefore("daily", at(10, 31, 20, 0)).orElseThrow();

        assertEquals(at(10, 29, 23, 30), found.range().startMs());
        assertEquals(at(10, 30, 0, 30), found.range().endMs());
        assertEquals(6, found.total());
    }

    @Test
    public void test_findBefore_streamTwentyDaysBack_found() throws IOException {
        writeDay(10, 11, evening(10, 11));
        writeDay(10, 31, live(10, 31, 20, 0) + chat(10, 31, 20, 1, "alice"));

        PastStream found = findBefore("daily", at(10, 31, 20, 0)).orElseThrow();

        assertEquals(at(10, 11, 20, 0), found.range().startMs());
        assertEquals(at(10, 11, 22, 0), found.range().endMs());
    }

    @Test
    public void test_findBefore_noEarlierStream_empty() throws IOException {
        writeDay(10, 31, live(10, 31, 20, 0) + chat(10, 31, 20, 1, "alice"));

        assertTrue(findBefore("daily", at(10, 31, 20, 0)).isEmpty());
    }

    @Test
    public void test_findBefore_neverSplit_findsInSingleFile() throws IOException {
        String content = evening(10, 30) + live(10, 31, 20, 0) + chat(10, 31, 20, 1, "alice");
        Files.writeString(dir.resolve("#testchan.log"), content, StandardCharsets.UTF_8);

        PastStream found = findBefore("never", at(10, 31, 20, 0)).orElseThrow();

        assertEquals(at(10, 30, 20, 0), found.range().startMs());
        assertEquals(at(10, 30, 22, 0), found.range().endMs());
    }

    @Test
    public void test_findBefore_preStreamChatOnly_notReturned() throws IOException {
        StringBuilder content = new StringBuilder();
        // Lasts over 30 minutes so the pre-LIVE chat alone would pass the marker-less duration rule.
        for (int minute = 15; minute < 60; minute += 3) {
            content.append(chat(10, 31, 19, minute, "alice"));
        }
        content.append(live(10, 31, 20, 0)).append(chat(10, 31, 20, 1, "alice"));
        writeDay(10, 31, content.toString());

        assertTrue(findBefore("daily", at(10, 31, 20, 0)).isEmpty());
    }

    @Test
    public void test_findBefore_fallBackWindowEdge_streamFoundWhole() throws IOException {
        ZoneId newYork = ZoneId.of("America/New_York");
        StringBuilder first = new StringBuilder(live(10, 31, 23, 0));
        for (int minute = 0; minute < 60; minute += 5) {
            first.append(chat(10, 31, 23, minute, "alice"));
        }
        StringBuilder second = new StringBuilder();
        // Hours 0 and 2 plus the repeated hour 1 (written twice, once per pass).
        for (int hour : new int[]{0, 1, 1, 2}) {
            for (int minute = 0; minute < 60; minute += 5) {
                second.append(String.format("[2026-11-01 %02d:%02d:00] <bob> hi\n", hour, minute));
            }
        }
        second.append("[2026-11-01 03:00:00] ** testchan is now OFFLINE **\n");
        writeDay(10, 31, first.toString());
        Files.writeString(dir.resolve("2026-11-01_#testchan.log"), second.toString(), StandardCharsets.UTF_8);
        // The window edge lands at 01:10 in the second (EST) pass of the repeated hour.
        long edge = LocalDateTime.of(2026, 11, 1, 1, 10).atZone(newYork)
                .withLaterOffsetAtOverlap().toInstant().toEpochMilli();
        long beforeMs = edge + java.time.Duration.ofDays(7).toMillis();

        ChannelLogReader reader = new ChannelLogReader(dir, CHANNEL, "daily", newYork);
        PastStream found = new PastStreamFinder(reader, CHANNEL, newYork).findBefore(beforeMs).orElseThrow();

        assertEquals(LocalDateTime.of(2026, 10, 31, 23, 0).atZone(newYork).toInstant().toEpochMilli(),
                found.range().startMs());
        assertEquals(60, found.total());
    }

    @Test
    public void test_findBefore_streamStraddlingLeftEdge_foundWhole() throws IOException {
        // Stream 10-23 20:00 to 10-24 02:00; beforeMs 10-31 01:00 puts the first
        // window's left edge (10-24 01:00) inside it, so it is first discarded as cut.
        StringBuilder first = new StringBuilder(live(10, 23, 20, 0));
        for (int minute = 0; minute < 240; minute += 20) {
            first.append(chat(10, 23, 20 + minute / 60, minute % 60, "alice"));
        }
        StringBuilder second = new StringBuilder();
        for (int minute = 0; minute < 120; minute += 20) {
            second.append(chat(10, 24, minute / 60, minute % 60, "bob"));
        }
        second.append(offline(10, 24, 2, 0));
        writeDay(10, 23, first.toString());
        writeDay(10, 24, second.toString());
        writeDay(10, 31, live(10, 31, 1, 0) + chat(10, 31, 1, 1, "alice"));

        PastStream found = findBefore("daily", at(10, 31, 1, 0)).orElseThrow();

        assertEquals(at(10, 23, 20, 0), found.range().startMs());
        assertEquals(at(10, 24, 2, 0), found.range().endMs());
        assertEquals(12 + 6, found.total());
    }

    @Test
    public void test_findBefore_widensThenRunsOut_empty() throws IOException {
        // Oldest file is 20 days back, so the 7 and 14 day windows miss it and the 28 day one reaches it.
        writeDay(10, 11, "[2026-10-11 20:00:00] some info line\n");
        writeDay(10, 31, live(10, 31, 20, 0) + chat(10, 31, 20, 1, "alice"));

        assertTrue(findBefore("daily", at(10, 31, 20, 0)).isEmpty());
    }
}
