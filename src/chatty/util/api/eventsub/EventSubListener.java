
package chatty.util.api.eventsub;

/**
 *
 * @author tduva
 */
public interface EventSubListener {
    void messageReceived(Message message);
    void info(String info);
}
