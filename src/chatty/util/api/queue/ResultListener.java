
package chatty.util.api.queue;

/**
 *
 * @author tduva
 */
public interface ResultListener {
    
    void result(Result r);

    record Result(String text, int responseCode, String errorText) {

    }
    
}
