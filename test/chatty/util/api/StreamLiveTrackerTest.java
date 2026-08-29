package chatty.util.api;

import chatty.util.api.StreamLiveTracker.Transition;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StreamLiveTrackerTest {

    @Test
    public void test_update_firstObservationOnline_returnsNone() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        assertEquals(Transition.NONE, tracker.update("teststream", true));
    }

    @Test
    public void test_update_firstObservationOffline_returnsNone() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        assertEquals(Transition.NONE, tracker.update("teststream", false));
    }

    @Test
    public void test_update_offlineToOnline_returnsWentLive() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        tracker.update("teststream", false);
        assertEquals(Transition.WENT_LIVE, tracker.update("teststream", true));
    }

    @Test
    public void test_update_onlineToOffline_returnsWentOffline() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        tracker.update("teststream", true);
        assertEquals(Transition.WENT_OFFLINE, tracker.update("teststream", false));
    }

    @Test
    public void test_update_repeatedSameState_returnsNone() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        tracker.update("teststream", true);
        assertEquals(Transition.NONE, tracker.update("teststream", true));
        assertEquals(Transition.NONE, tracker.update("teststream", true));
    }

    @Test
    public void test_update_afterForget_returnsNone() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        tracker.update("teststream", true);
        tracker.forget("teststream");
        assertEquals(Transition.NONE, tracker.update("teststream", false));
    }

    @Test
    public void test_update_separateStreams_trackedIndependently() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        tracker.update("teststream", false);
        tracker.update("otherstream", true);
        assertEquals(Transition.WENT_LIVE, tracker.update("teststream", true));
        assertEquals(Transition.WENT_OFFLINE, tracker.update("otherstream", false));
    }

    @Test
    public void test_forget_nullStream_leavesOtherStateIntact() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        tracker.update("teststream", true);
        tracker.forget(null);
        assertEquals(Transition.WENT_OFFLINE, tracker.update("teststream", false));
    }
}
