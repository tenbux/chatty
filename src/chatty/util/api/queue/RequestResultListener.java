
package chatty.util.api.queue;

/**
 *
 * @author tduva
 */
public interface RequestResultListener {
    
    void requestResult(String result, int responseCode, String errorResult, int ratelimitRemaining);
    
}
