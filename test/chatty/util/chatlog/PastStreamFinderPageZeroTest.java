package chatty.util.chatlog;

import chatty.util.chatlog.PastStreamFinder.PastStream;
import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import chatty.util.chatlog.StreamRangeResolver.TwitchStreamState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Feeds the start that {@link StreamLeaderboard} resolves for page 0 to the
 * finder, as the dialog's Back button does.
 */
public class PastStreamFinderPageZeroTest {

    private static final String CHANNEL = "#testchan";
    private static final ZoneId ZONE = ZoneId.of("UTC");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static long at(int day, int hour, int minute) {
        return LocalDateTime.of(2026, 10, day, hour, minute).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static String line(int day, int hour, int minute, String text) {
        return String.format("[2026-10-%02d %02d:%02d:00] %s\n", day, hour, minute, text);
    }

    /**
     * Day 30 holds the stream to find. Day 31 holds six chats before page 0's
     * LIVE marker, which must not be mistaken for a past stream, then page 0's
     * own stream.
     */
    private Path writeLogs() throws IOException {
        Path dir = folder.getRoot().toPath();
        StringBuilder previous = new StringBuilder(line(30, 20, 0, "** testchan is now LIVE **"));
        for (int i = 0; i < 6; i++) {
            previous.append(line(30, 20, 5 + i, "<alice> hi"));
        }
        previous.append(line(30, 20, 30, "** testchan is now OFFLINE **"));
        StringBuilder newest = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            newest.append(line(31, 19, 40 + i * 3, "<bob> pre"));
        }
        newest.append(line(31, 20, 0, "** testchan is now LIVE **"));
        for (int i = 0; i < 3; i++) {
            newest.append(line(31, 20, 10 + i, "<carol> hi"));
        }
        newest.append(line(31, 20, 30, "** testchan is now OFFLINE **"));
        Files.writeString(dir.resolve("2026-10-30_#testchan.log"), previous, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("2026-10-31_#testchan.log"), newest, StandardCharsets.UTF_8);
        return dir;
    }

    private void assertPreviousStreamFound(TwitchStreamState twitch, long expectedPageZeroStartMs) throws IOException {
        Path dir = writeLogs();
        StreamLeaderboard board = new StreamLeaderboard(
                new ChannelLogReader(dir, CHANNEL, "daily", ZONE), ZONE, () -> at(31, 23, 0));
        StreamRange pageZero = (StreamRange) board.refresh(twitch).resolution();
        assertEquals(expectedPageZeroStartMs, pageZero.startMs());

        PastStream previous = new PastStreamFinder(new ChannelLogReader(dir, CHANNEL, "daily", ZONE), CHANNEL, ZONE)
                .findBefore(pageZero.startMs()).orElseThrow();

        assertEquals(at(30, 20, 0), previous.range().startMs());
        assertEquals(at(30, 20, 30), previous.range().endMs());
        assertEquals(6, previous.total());
    }

    @Test
    public void test_findBefore_pageZeroFromMarkersOnly_previousStreamFound() throws IOException {
        assertPreviousStreamFound(new TwitchStreamState(false, -1, -1), at(31, 20, 0));
    }

    @Test
    public void test_findBefore_pageZeroStartedBeforeLiveMarker_previousStreamFound() throws IOException {
        assertPreviousStreamFound(new TwitchStreamState(true, at(31, 19, 58), -1), at(31, 19, 58));
    }
}
