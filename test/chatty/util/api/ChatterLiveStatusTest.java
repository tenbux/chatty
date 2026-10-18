package chatty.util.api;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChatterLiveStatusTest {

    private static ChatterLiveStatus create() {
        return new ChatterLiveStatus(ids -> {});
    }

    @Test
    public void test_parseLiveIds_twoStreams_returnsBothIds() {
        String json = "{\"data\":[{\"user_id\":\"1\"},{\"user_id\":\"2\"}],\"pagination\":{}}";
        assertEquals(Set.of("1", "2"), ChatterLiveStatus.parseLiveIds(json));
    }

    @Test
    public void test_parseLiveIds_invalidJson_returnsNull() {
        assertNull(ChatterLiveStatus.parseLiveIds("{"));
        assertNull(ChatterLiveStatus.parseLiveIds(null));
    }

    @Test
    public void test_isLive_afterResult_liveTrueOfflineFalse() {
        ChatterLiveStatus status = create();
        status.resultReceived(Set.of("1", "2"), Set.of("1"));
        assertTrue(status.isLive("1"));
        assertFalse(status.isLive("2"));
    }

    @Test
    public void test_isLive_afterError_returnsFalse() {
        ChatterLiveStatus status = create();
        status.resultReceived(Set.of("3"), null);
        assertFalse(status.isLive("3"));
    }

    @Test
    public void test_isLive_nullOrEmptyId_returnsFalse() {
        ChatterLiveStatus status = create();
        assertFalse(status.isLive(null));
        assertFalse(status.isLive(""));
        assertEquals(0, status.pendingQueries());
    }

    @Test
    public void test_isLive_unknownId_queuesQuery() {
        ChatterLiveStatus status = create();
        assertFalse(status.isLive("4"));
        assertEquals(1, status.pendingQueries());
        assertFalse(status.isLive("4"));
        assertEquals(1, status.pendingQueries());
    }

    @Test
    public void test_requester_receivesAtMost100Ids() {
        Set<String> captured = new HashSet<>();
        ChatterLiveStatus status = new ChatterLiveStatus(captured::addAll);
        for (int i = 0; i < 150; i++) {
            status.isLive("id" + i);
        }
        status.requestNow();
        assertEquals(100, captured.size());
    }

    @Test
    public void test_isLive_afterOneMinute_returnsFalseUntilRechecked() {
        ChatterLiveStatus status = create();
        status.setCurrentTimestamp(0);
        status.resultReceived(Set.of("1"), Set.of("1"));
        status.setCurrentTimestamp(59_999);
        assertTrue(status.isLive("1"));
        assertEquals(0, status.pendingQueries());

        status.setCurrentTimestamp(60_000);
        assertFalse(status.isLive("1"));
        assertEquals(1, status.pendingQueries());

        status.resultReceived(Set.of("1"), Set.of("1"));
        assertTrue(status.isLive("1"));
    }
}
