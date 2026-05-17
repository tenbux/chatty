
package chatty.util.api.eventsub;

import chatty.util.jws.JWSClient;

/**
 *
 * @author tduva
 */
interface ConnectionsMessageHandler {
    
    void handleReceived(int connection, String text, Message message);

    void handleSent(int connection, String text);

    void handleConnect(int connection, JWSClient c);

    void handleDisconnect(int connection);
    
    void handleRegisterError(int responseCode);
    
}
