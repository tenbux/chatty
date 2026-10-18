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
        data.setCacheTimes(1, 30, TimeUnit.MINUTES);
    }

    /**
     * Get the cached live status, queueing a request if it is missing or due
     * for refresh.
     *
     * @param userId The user ID
     * @return true only if the chatter is known to be live, false if offline,
     * not yet known, due for refresh or on error
     */
    public boolean isLive(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        // The ID is its own unique key so each chatter keeps a pending query;
        // a shared key would replace queries of other chatters in busy chats.
        // getOrQuery leaves out values due for refresh (unlike
        // getOrQuerySingle), so a stream that just ended stops showing the
        // icon until a new request confirms it is still live.
        CachedBulkManager.Result<String, Boolean> result = data.getOrQuery(userId, null, CachedBulkManager.NONE, userId);
        return result != null && Boolean.TRUE.equals(result.get(userId));
    }

    /**
     * Get the live status for a single chatter, requested immediately if it is
     * not cached or if a refresh is forced. The result also updates the status
     * used for chat messages.
     *
     * @param userId The user ID
     * @param refresh Ignore any cached status and request it again
     * @param result Receives true (live), false (offline) or null (unknown),
     * possibly on another thread; without refresh a failed request may
     * report the previously cached status instead of null
     */
    public void getLiveStatus(String userId, boolean refresh, Consumer<Boolean> result) {
        if (userId == null || userId.isEmpty()) {
            result.accept(null);
            return;
        }
        int options = CachedBulkManager.ASAP;
        if (refresh) {
            options |= CachedBulkManager.REFRESH;
        }
        // A fresh unique object, since the chat lookup re-queries with the ID
        // as unique key and would otherwise replace this query, dropping the
        // listener.
        data.query(null, r -> result.accept(r.get(userId)), options, userId);
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

    void setCurrentTimestamp(long timestamp) {
        data.setCurrentTimestamp(timestamp);
    }

}
