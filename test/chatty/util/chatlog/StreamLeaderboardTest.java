package chatty.util.chatlog;

import chatty.util.chatlog.ChatterTally.Entry;
import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import chatty.util.chatlog.StreamRangeResolver.TwitchStreamState;
import chatty.util.chatlog.StreamRangeResolver.Unavailable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StreamLeaderboardTest {

    private static final String CHANNEL = "#testchan";
    private static final String LOG_FILE = "2026-10-31_#testchan.log";
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final long NOW_MS = at(23, 59, 0);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path dir;
    private StreamLeaderboard leaderboard;

    @Before
    public void setUp() {
        dir = folder.getRoot().toPath();
        leaderboard = new StreamLeaderboard(
                new ChannelLogReader(dir, CHANNEL, "daily", ZONE), ZONE, () -> NOW_MS);
    }

    private static long at(int hour, int minute, int second) {
        return LocalDateTime.of(2026, 10, 31, hour, minute, second).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static String time(int hour, int minute, int second) {
        return String.format("[2026-10-31 %02d:%02d:%02d]", hour, minute, second);
    }

    private static String chat(int hour, int minute, int second, String name) {
        return time(hour, minute, second) + " <" + name + "> hello\n";
    }

    private static String marker(int hour, int minute, int second, String state) {
        return time(hour, minute, second) + " ** Test Streamer is now " + state + " **\n";
    }

    private void append(String content) throws IOException {
        Files.writeString(dir.resolve(LOG_FILE), content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Test
    public void test_refresh_live_countsFromStart() throws IOException {
        append(chat(19, 0, 0, "viewer_one")
                + chat(19, 59, 59, "viewer_one")
                + chat(20, 0, 0, "viewer_one")
                + chat(20, 5, 0, "viewer_two"));

        StreamLeaderboard.Snapshot snapshot = leaderboard.refresh(
                new TwitchStreamState(true, at(20, 0, 0), -1));

        assertEquals(new StreamRange(at(20, 0, 0), Long.MAX_VALUE, true), snapshot.resolution());
        assertEquals(2, snapshot.total());
        assertEquals(List.of(new Entry("viewer_one", 1), new Entry("viewer_two", 1)), snapshot.ranking());
    }

    @Test
    public void test_refresh_backfilledLineOutsideRange_excluded() throws IOException {
        TwitchStreamState live = new TwitchStreamState(true, at(20, 0, 0), -1);
        append(chat(20, 1, 0, "viewer_one"));
        assertEquals(1, leaderboard.refresh(live).total());

        append(chat(20, 2, 0, "viewer_two"));
        append(chat(19, 30, 0, "viewer_two"));
        StreamLeaderboard.Snapshot snapshot = leaderboard.refresh(live);

        assertEquals(2, snapshot.total());
        assertEquals(List.of(new Entry("viewer_one", 1), new Entry("viewer_two", 1)), snapshot.ranking());
    }

    @Test
    public void test_refresh_endedThenLive_resetsToNewStream() throws IOException {
        append(marker(18, 0, 0, "LIVE")
                + chat(18, 30, 0, "viewer_one")
                + marker(19, 0, 0, "OFFLINE")
                + chat(20, 30, 0, "viewer_two"));
        StreamLeaderboard.Snapshot first = leaderboard.refresh(new TwitchStreamState(false, -1, -1));
        assertEquals(new StreamRange(at(18, 0, 0), at(19, 0, 0), false), first.resolution());
        assertEquals(List.of(new Entry("viewer_one", 1)), first.ranking());

        append(marker(21, 0, 0, "LIVE")
                + chat(21, 10, 0, "viewer_two")
                + chat(21, 11, 0, "viewer_two"));
        StreamLeaderboard.Snapshot second = leaderboard.refresh(new TwitchStreamState(true, at(21, 0, 0), -1));

        assertEquals(new StreamRange(at(21, 0, 0), Long.MAX_VALUE, true), second.resolution());
        assertEquals(List.of(new Entry("viewer_two", 2)), second.ranking());
        assertEquals(2, second.total());
    }

    @Test
    public void test_refresh_restartAfterShortDrop_includesFirstSegment() throws IOException {
        append(marker(19, 0, 0, "LIVE")
                + chat(19, 30, 0, "viewer_one")
                + marker(19, 57, 0, "OFFLINE")
                + chat(20, 1, 0, "viewer_two"));

        StreamLeaderboard.Snapshot snapshot = leaderboard.refresh(
                new TwitchStreamState(true, at(20, 0, 0), -1));

        assertEquals(new StreamRange(at(19, 0, 0), Long.MAX_VALUE, true), snapshot.resolution());
        assertEquals(List.of(new Entry("viewer_one", 1), new Entry("viewer_two", 1)), snapshot.ranking());
    }

    @Test
    public void test_refresh_fileReplacedMidStream_noDoubleCount() throws IOException {
        TwitchStreamState live = new TwitchStreamState(true, at(20, 0, 0), -1);
        append(chat(19, 0, 0, "viewer_one")
                + chat(20, 1, 0, "viewer_one")
                + chat(20, 2, 0, "viewer_two"));
        assertEquals(2, leaderboard.refresh(live).total());

        // Starts differently from the old file, as when the log is rotated or rewritten externally.
        Files.writeString(dir.resolve(LOG_FILE),
                chat(20, 1, 0, "viewer_one") + chat(20, 2, 0, "viewer_two") + chat(20, 3, 0, "viewer_two"),
                StandardCharsets.UTF_8);
        StreamLeaderboard.Snapshot snapshot = leaderboard.refresh(live);

        assertEquals(3, snapshot.total());
        assertEquals(List.of(new Entry("viewer_two", 2), new Entry("viewer_one", 1)), snapshot.ranking());
    }

    @Test
    public void test_refresh_unavailable_emptySnapshotThenRecovers() throws IOException {
        append(chat(20, 1, 0, "viewer_one"));
        StreamLeaderboard.Snapshot first = leaderboard.refresh(new TwitchStreamState(false, -1, -1));
        assertTrue(first.resolution() instanceof Unavailable);
        assertTrue(first.ranking().isEmpty());
        assertEquals(0, first.total());

        StreamLeaderboard.Snapshot second = leaderboard.refresh(new TwitchStreamState(true, at(20, 0, 0), -1));

        assertEquals(1, second.total());
    }

    @Test
    public void test_refresh_noLogFiles_emptyRange() throws IOException {
        StreamLeaderboard.Snapshot snapshot = leaderboard.refresh(
                new TwitchStreamState(true, at(20, 0, 0), -1));

        assertEquals(0, snapshot.total());
        assertTrue(snapshot.ranking().isEmpty());
    }
}
