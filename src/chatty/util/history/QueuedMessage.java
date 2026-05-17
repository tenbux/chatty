
package chatty.util.history;

import chatty.User;
import chatty.util.irc.MsgTags;

/**
 *
 * @author tduva
 */
public record QueuedMessage(User user, String text, boolean action, MsgTags tags) {

}
