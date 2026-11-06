package chatty.util.chatlog;

import chatty.util.chatlog.ChatterTally.Entry;
import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LeaderboardPostTest {

    @Test
    public void test_formatLine_rankNameAndCount() {
        assertEquals("#1 a: 342 messages", LeaderboardPost.formatLine(1, new Entry("a", 342)));
    }

    @Test
    public void test_formatLine_oneMessage_singular() {
        assertEquals("#12 a: 1 message", LeaderboardPost.formatLine(12, new Entry("a", 1)));
    }

    @Test
    public void test_formatLine_largeCount_grouped() {
        assertEquals("#1 a: 1,204 messages", LeaderboardPost.formatLine(1, new Entry("a", 1204)));
    }

    @Test
    public void test_formatCopyHeader_ended_dateAndRunTime() {
        StreamRange range = new StreamRange(at(1, 15, 30), at(1, 23, 0), false);

        assertEquals("#xara stream, Nov 1, 7hr 30min, 2,431 messages",
                LeaderboardPost.formatCopyHeader("#xara", range, 2431, ZONE, at(2, 12, 0)));
    }

    @Test
    public void test_formatCopyHeader_endedAfterMidnight_startDate() {
        StreamRange range = new StreamRange(at(1, 23, 44), at(2, 1, 2), false);

        assertEquals("#xara stream, Nov 1, 1hr 18min, 10 messages",
                LeaderboardPost.formatCopyHeader("#xara", range, 10, ZONE, at(2, 12, 0)));
    }

    @Test
    public void test_formatCopyHeader_live_runTimeUntilNow() {
        StreamRange range = new StreamRange(at(2, 17, 0), Long.MAX_VALUE, true);

        assertEquals("#xara stream, Nov 2, live for 2hr 15min, 2,431 messages",
                LeaderboardPost.formatCopyHeader("#xara", range, 2431, ZONE, at(2, 19, 15)));
    }

    @Test
    public void test_formatCopyHeader_underAnHour_minutesOnly() {
        StreamRange range = new StreamRange(at(1, 19, 0), at(1, 19, 45), false);

        assertEquals("#xara stream, Nov 1, 45min, 312 messages",
                LeaderboardPost.formatCopyHeader("#xara", range, 312, ZONE, at(2, 12, 0)));
    }

    @Test
    public void test_formatCopyHeader_wholeHours_noMinutes() {
        StreamRange range = new StreamRange(at(1, 19, 0), at(1, 22, 0), false);

        assertEquals("#xara stream, Nov 1, 3hr, 980 messages",
                LeaderboardPost.formatCopyHeader("#xara", range, 980, ZONE, at(2, 12, 0)));
    }

    @Test
    public void test_formatCopyHeader_oneMessage_singular() {
        StreamRange range = new StreamRange(at(2, 19, 0), Long.MAX_VALUE, true);

        assertEquals("#xara stream, Nov 2, live for 0min, 1 message",
                LeaderboardPost.formatCopyHeader("#xara", range, 1, ZONE, at(2, 19, 0)));
    }

    private static final ZoneId ZONE = ZoneId.of("America/Chicago");

    private static long at(int day, int hour, int minute) {
        return ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, ZONE).toInstant().toEpochMilli();
    }

    @Test
    public void test_delay_privileged_zero() {
        assertEquals(0, LeaderboardPost.delayBetweenLinesMs(true, 0));
        assertEquals(0, LeaderboardPost.delayBetweenLinesMs(true, 30));
    }

    @Test
    public void test_delay_slowMode_plusOneSecond() {
        assertEquals(31000, LeaderboardPost.delayBetweenLinesMs(false, 30));
    }

    @Test
    public void test_delay_noSlowMode_1500() {
        assertEquals(1500, LeaderboardPost.delayBetweenLinesMs(false, 0));
    }
}
