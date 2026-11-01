package chatty.util.chatlog;

import chatty.util.chatlog.LeaderboardLogLine.StatusMarker;
import chatty.util.chatlog.StreamRangeResolver.Resolution;
import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import chatty.util.chatlog.StreamRangeResolver.TwitchStreamState;
import chatty.util.chatlog.StreamRangeResolver.Unavailable;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class StreamRangeResolverTest {

    private static final long BASE = 1_700_000_000_000L;
    private static final long MINUTE = 60 * 1000;

    private static long minutes(long value) {
        return BASE + value * MINUTE;
    }

    private static StatusMarker live(long atMinute) {
        return new StatusMarker(minutes(atMinute), true);
    }

    private static StatusMarker offline(long atMinute) {
        return new StatusMarker(minutes(atMinute), false);
    }

    private static TwitchStreamState liveStream(long startMinute) {
        return new TwitchStreamState(true, startMinute == -1 ? -1 : minutes(startMinute), -1);
    }

    @Test
    public void test_resolve_liveNoMarkers_usesTwitchStart() {
        Resolution result = StreamRangeResolver.resolve(List.of(), liveStream(100));
        assertEquals(new StreamRange(minutes(100), Long.MAX_VALUE, true), result);
    }

    @Test
    public void test_resolve_liveUnknownStart_unavailable() {
        Resolution result = StreamRangeResolver.resolve(List.of(), liveStream(-1));
        assertEquals(new Unavailable("Stream start unknown"), result);
    }

    @Test
    public void test_resolve_liveAfterShortDrop_extendsToEarlierLive() {
        Resolution result = StreamRangeResolver.resolve(List.of(live(10), offline(95)), liveStream(100));
        assertEquals(new StreamRange(minutes(10), Long.MAX_VALUE, true), result);
    }

    @Test
    public void test_resolve_liveAfterLongGap_doesNotExtend() {
        Resolution result = StreamRangeResolver.resolve(List.of(live(10), offline(80)), liveStream(100));
        assertEquals(new StreamRange(minutes(100), Long.MAX_VALUE, true), result);
    }

    @Test
    public void test_resolve_offlineMarkerSlightlyAfterStart_extendsToEarlierLive() {
        Resolution result = StreamRangeResolver.resolve(List.of(live(10), offline(105)), liveStream(100));
        assertEquals(new StreamRange(minutes(10), Long.MAX_VALUE, true), result);
    }

    @Test
    public void test_resolve_chainOfShortDrops_extendsAcrossAll() {
        Resolution result = StreamRangeResolver.resolve(
                List.of(live(10), offline(50), live(55), offline(95)), liveStream(100));
        assertEquals(new StreamRange(minutes(10), Long.MAX_VALUE, true), result);
    }

    @Test
    public void test_resolve_unsortedMarkers_sortedBeforeResolving() {
        Resolution result = StreamRangeResolver.resolve(
                List.of(offline(95), offline(50), live(55), live(10)), liveStream(100));
        assertEquals(new StreamRange(minutes(10), Long.MAX_VALUE, true), result);
    }

    @Test
    public void test_resolve_offlineWithSession_usesSessionTimes() {
        TwitchStreamState twitch = new TwitchStreamState(false, minutes(100), minutes(300));
        Resolution result = StreamRangeResolver.resolve(List.of(), twitch);
        assertEquals(new StreamRange(minutes(100), minutes(300), false), result);
    }

    @Test
    public void test_resolve_offlineFromLogs_lastPair() {
        TwitchStreamState twitch = new TwitchStreamState(false, -1, -1);
        Resolution result = StreamRangeResolver.resolve(List.of(live(10), offline(200)), twitch);
        assertEquals(new StreamRange(minutes(10), minutes(200), false), result);
    }

    @Test
    public void test_resolve_offlineFromLogsAfterShortDrop_extendsToEarlierLive() {
        TwitchStreamState twitch = new TwitchStreamState(false, -1, -1);
        Resolution result = StreamRangeResolver.resolve(
                List.of(live(10), offline(50), live(55), offline(200)), twitch);
        assertEquals(new StreamRange(minutes(10), minutes(200), false), result);
    }

    @Test
    public void test_resolve_offlineLastMarkerLive_unavailable() {
        TwitchStreamState twitch = new TwitchStreamState(false, -1, -1);
        Resolution result = StreamRangeResolver.resolve(List.of(live(10), offline(200), live(500)), twitch);
        assertEquals(new Unavailable("No recent stream found in the logs"), result);
    }

    @Test
    public void test_resolve_offlineNoMarkers_unavailable() {
        TwitchStreamState twitch = new TwitchStreamState(false, -1, -1);
        Resolution result = StreamRangeResolver.resolve(List.of(), twitch);
        assertEquals(new Unavailable("No recent stream found in the logs"), result);
    }
}
