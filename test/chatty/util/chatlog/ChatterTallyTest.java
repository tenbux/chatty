package chatty.util.chatlog;

import chatty.util.chatlog.ChatterTally.Entry;
import chatty.util.chatlog.LeaderboardLogLine.ChatLine;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ChatterTallyTest {

    private static ChatLine chat(long timeMs, String name) {
        return new ChatLine(timeMs, name.toLowerCase(), name, false);
    }

    private static ChatLine bot(long timeMs, String name) {
        return new ChatLine(timeMs, name.toLowerCase(), name, true);
    }

    @Test
    public void test_ranking_ordersByCountDesc() {
        ChatterTally tally = new ChatterTally();
        tally.add(chat(1, "viewer_one"));
        tally.add(chat(2, "viewer_two"));
        tally.add(chat(3, "viewer_two"));
        tally.add(chat(4, "viewer_two"));
        tally.add(chat(5, "viewer_three"));
        tally.add(chat(6, "viewer_three"));

        assertEquals(List.of(
                new Entry("viewer_two", 3),
                new Entry("viewer_three", 2),
                new Entry("viewer_one", 1)), tally.ranking());
        assertEquals(6, tally.total());
    }

    @Test
    public void test_ranking_tieGoesToEarlierFirstMessage() {
        ChatterTally tally = new ChatterTally();
        tally.add(chat(2, "viewer_two"));
        tally.add(chat(1, "viewer_one"));

        assertEquals(List.of(
                new Entry("viewer_one", 1),
                new Entry("viewer_two", 1)), tally.ranking());
    }

    @Test
    public void test_ranking_tieWithEqualFirstMessageTimeOrdersByKey() {
        ChatterTally tally = new ChatterTally();
        tally.add(chat(1, "viewer_two"));
        tally.add(chat(1, "viewer_one"));

        assertEquals(List.of(
                new Entry("viewer_one", 1),
                new Entry("viewer_two", 1)), tally.ranking());
    }

    @Test
    public void test_ranking_tieUsesEarliestTimeEvenWhenAddedLate() {
        ChatterTally tally = new ChatterTally();
        tally.add(chat(5, "viewer_one"));
        tally.add(chat(6, "viewer_two"));
        // Backfilled line that predates everything else for viewer_two.
        tally.add(chat(1, "viewer_two"));
        tally.add(chat(7, "viewer_one"));

        assertEquals(List.of(
                new Entry("viewer_two", 2),
                new Entry("viewer_one", 2)), tally.ranking());
    }

    @Test
    public void test_add_bot_excludedFromRankingAndTotal() {
        ChatterTally tally = new ChatterTally();
        tally.add(chat(1, "viewer_one"));
        tally.add(bot(2, "testbot"));
        tally.add(bot(3, "testbot"));

        assertEquals(List.of(new Entry("viewer_one", 1)), tally.ranking());
        assertEquals(1, tally.total());
    }

    @Test
    public void test_ranking_usesLatestCapitalization() {
        ChatterTally tally = new ChatterTally();
        tally.add(chat(1, "viewer_one"));
        tally.add(chat(2, "Viewer_One"));

        assertEquals(List.of(new Entry("Viewer_One", 2)), tally.ranking());
    }

    @Test
    public void test_ranking_latestCapitalizationWinsWhenAddedOutOfOrder() {
        ChatterTally tally = new ChatterTally();
        tally.add(chat(2, "Viewer_One"));
        tally.add(chat(1, "viewer_one"));

        assertEquals(List.of(new Entry("Viewer_One", 2)), tally.ranking());
    }

    @Test
    public void test_ranking_empty_returnsEmptyList() {
        ChatterTally tally = new ChatterTally();

        assertTrue(tally.ranking().isEmpty());
        assertEquals(0, tally.total());
    }

    @Test
    public void test_ranking_calledTwice_returnsIndependentLists() {
        ChatterTally tally = new ChatterTally();
        tally.add(chat(1, "viewer_one"));

        List<Entry> first = tally.ranking();
        tally.add(chat(2, "viewer_two"));

        assertNotSame(first, tally.ranking());
        assertEquals(1, first.size());
        assertEquals(2, tally.ranking().size());
    }
}
