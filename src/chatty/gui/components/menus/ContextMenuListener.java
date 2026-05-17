
package chatty.gui.components.menus;

import chatty.Room;
import chatty.User;
import chatty.gui.components.Channel;
import chatty.util.api.Emoticon;
import chatty.util.api.CachedImage;
import chatty.util.api.StreamInfo;
import chatty.util.api.usericons.Usericon;
import chatty.util.dnd.DockContent;
import java.awt.event.ActionEvent;
import java.util.Collection;

/**
 *
 * @author tduva
 */
public interface ContextMenuListener {
    void userMenuItemClicked(ActionEvent e, User user, String msgId, String autoModMsgId);
    void urlMenuItemClicked(ActionEvent e, String url);
    void menuItemClicked(ActionEvent e);
    void textMenuItemClick(ActionEvent e, String selected);
    void roomsMenuItemClicked(ActionEvent e, Collection<Room> rooms);
    void channelMenuItemClicked(ActionEvent e, Channel channel);
    void tabMenuItemClicked(ActionEvent e, DockContent content);
    void streamsMenuItemClicked(ActionEvent e, Collection<String> streams);
    void streamInfosMenuItemClicked(ActionEvent e, Collection<StreamInfo> streamInfos);
    void emoteMenuItemClicked(ActionEvent e, CachedImage<Emoticon> emote);
    void usericonMenuItemClicked(ActionEvent e, CachedImage<Usericon> usericonImage);
}
