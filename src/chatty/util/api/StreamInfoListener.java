
package chatty.util.api;


/**
 * Notify about changes in the stream status and about general stream info
 * updates.
 * 
 * @author tduva
 */
public interface StreamInfoListener {
    
    void streamInfoUpdated(StreamInfo info);
    void streamInfoStatusChanged(StreamInfo info, String newStatus);
    
}
