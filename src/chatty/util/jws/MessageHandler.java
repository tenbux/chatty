
package chatty.util.jws;

/**
 *
 * @author tduva
 */
public interface MessageHandler {

    void handleReceived(String text);

    void handleSent(String text);

    void handleConnect(JWSClient c);

    void handleDisconnect(int code);
}
