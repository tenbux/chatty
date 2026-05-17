
package chatty.util.ffz;

import chatty.util.api.usericons.Usericon;
import chatty.util.api.EmoticonUpdate;
import java.util.List;
import java.util.Set;

/**
 *
 * @author tduva
 */
public interface FrankerFaceZListener {
    
    /**
     * This may be called out of a lock on the WebsocketClient instance, if
     * originating from there.
     * 
     * @param emotes 
     */
    void channelEmoticonsReceived(EmoticonUpdate emotes);
    void usericonsReceived(List<Usericon> icons);
    void botNamesReceived(String stream, Set<String> botNames);
    void wsInfo(String info);
    void authorizeUser(String code);
    void wsUserInfo(String info);
}
