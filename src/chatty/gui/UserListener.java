
package chatty.gui;

import chatty.User;
import chatty.gui.components.Channel;
import chatty.util.api.Emoticon;
import chatty.util.api.usericons.Usericon;
import chatty.util.irc.MsgTags;
import java.awt.event.MouseEvent;

/**
 *
 * @author tduva
 */
public interface UserListener {
    
    /**
     * 
     * @param user
     * @param e Can be null
     */
    void userClicked(User user, String messageId, String autoModMsgId, MouseEvent e);
    void emoteClicked(Emoticon emote, MouseEvent e);
    void usericonClicked(Usericon usericon, MouseEvent e);
    void linkClicked(Channel channel, MsgTags.Link link);

}
