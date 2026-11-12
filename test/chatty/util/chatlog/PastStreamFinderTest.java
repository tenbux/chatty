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
        for (int minute = 40; minute < 60; minute += 3) {
            content.append(chat(10, 31, 19, minute, "alice"));
        }
        content.append(live(10, 31, 20, 0)).append(chat(10, 31, 20, 1, "alice"));
        writeDay(10, 31, content.toString());

        assertTrue(findBefore("daily", at(10, 31, 20, 0)).isEmpty());
    }
}
