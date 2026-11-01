package chatty.util.chatlog;

import chatty.util.chatlog.ChatterTally.Entry;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LeaderboardPostTest {

    @Test
    public void test_formatTop_threeLines() {
        List<String> lines = LeaderboardPost.formatTop(List.of(
                new Entry("a", 342), new Entry("b", 289), new Entry("c", 201), new Entry("d", 5)));

        assertEquals(List.of("#1 a: 342 messages", "#2 b: 289 messages", "#3 c: 201 messages"), lines);
    }

    @Test
    public void test_formatTop_singularAndFewer() {
        assertEquals(List.of("#1 a: 1 message"), LeaderboardPost.formatTop(List.of(new Entry("a", 1))));
    }

    @Test
    public void test_formatTop_emptyRanking_noLines() {
        assertTrue(LeaderboardPost.formatTop(List.of()).isEmpty());
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
