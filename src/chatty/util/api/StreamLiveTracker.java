package chatty.util.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last known online state of streams and reports when it changes.
 *
 * The first observation of a stream only records the state, so joining a
 * channel that is already live does not look like the stream just going live.
 */
public class StreamLiveTracker {

    public enum Transition {
        NONE, WENT_LIVE, WENT_OFFLINE
    }

    /**
     * Concurrent because state is recorded from the API thread while it can be
     * dropped from the thread closing a channel.
     */
    private final Map<String, Boolean> lastOnline = new ConcurrentHashMap<>();

    /**
     * Record the current online state of a stream and report whether it
     * changed compared to the previous call.
     *
     * @param stream The stream name, must not be null
     * @param isOnline Whether the stream is currently online
     * @return WENT_LIVE or WENT_OFFLINE if the state changed, otherwise NONE,
     * which includes the first observation of this stream
     */
    public Transition update(String stream, boolean isOnline) {
        Boolean previous = lastOnline.put(stream, isOnline);
        if (previous == null || previous == isOnline) {
            return Transition.NONE;
        }
        return isOnline ? Transition.WENT_LIVE : Transition.WENT_OFFLINE;
    }

    /**
     * Drop the recorded state for a stream, so the next update() for it counts
     * as a first observation again.
     *
     * @param stream The stream name, null is ignored
     */
    public void forget(String stream) {
        if (stream != null) {
            lastOnline.remove(stream);
        }
    }
}
