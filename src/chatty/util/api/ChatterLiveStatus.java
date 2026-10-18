package chatty.util.api;

import chatty.util.CachedBulkManager;
import chatty.util.Debugging;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Cache of whether chatters (by user ID) are currently live, filled by batched
 * requests.
 */
public class ChatterLiveStatus {

    private static final Logger LOGGER = Logger.getLogger(ChatterLiveStatus.class.getName());

    private final CachedBulkManager<String, Boolean> data;

    /**
     * @param requester Performs the actual request for the given user IDs and
     * is expected to report back via {@link #resultReceived}
     */
    public ChatterLiveStatus(Consumer<Set<String>> requester) {
        data = new CachedBulkManager<>((manager, asap, normal, backlog) -> {
            Set<String> toRequest = manager.makeAndSetRequested(asap, normal, backlog, 100);
            Debugging.println("livechatters", "Requesting %d chatter IDs", toRequest.size());
            requester.accept(toRequest);
        }, "[LiveChatters] ", CachedBulkManager.DAEMON);
        data.setCacheTimes(2, 30, TimeUnit.MINUTES);
    }

    /**
     * Get the cached live status, queueing a request if it is missing or due
     * for refresh.
     *
     * @param userId The user ID
     * @return true only if the chatter is known to be live, false if offline,
     * not yet known or on error
     */
    public boolean isLive(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        // The ID is its own unique key so each chatter keeps a pending query;
        // a shared key would replace queries of other chatters in busy chats.
        return Boolean.TRUE.equals(data.getOrQuerySingle(userId, null, CachedBulkManager.NONE, userId));
    }

    /**
     * @param requested All IDs that were part of the request
     * @param liveIds IDs of the requested chatters that are live, or null if
     * the request failed
     */
    public void resultReceived(Set<String> requested, Set<String> liveIds) {
        if (liveIds == null) {
            data.setError(requested);
            return;
        }
        // One batched call, since each setResult scans all pending queries.
        Map<String, Boolean> results = new HashMap<>();
        for (String id : requested) {
            results.put(id, liveIds.contains(id));
        }
        data.setResult(results);
    }

    /**
     * Parse a Get Streams response.
     *
     * @param json The response body
     * @return The user IDs of all returned streams, or null if the response
     * could not be parsed
     */
    public static Set<String> parseLiveIds(String json) {
        if (json == null) {
            return null;
        }
        try {
            Set<String> result = new HashSet<>();
            JSONObject root = (JSONObject) new JSONParser().parse(json);
            JSONArray array = (JSONArray) root.get("data");
            for (Object o : array) {
                if (o instanceof JSONObject) {
                    Object id = ((JSONObject) o).get("user_id");
                    if (id instanceof String) {
                        result.add((String) id);
                    }
                }
            }
            return result;
        }
        catch (Exception ex) {
            LOGGER.warning("Error parsing live chatter streams: " + ex);
        }
        return null;
    }

    /**
     * Run the request pass immediately instead of waiting for the timer.
     */
    void requestNow() {
        data.doRequests();
    }

    int pendingQueries() {
        return data.pendingRequests();
    }

}
