
package chatty.util.history;

import chatty.util.irc.MsgTags;

/**
 * A single backfilled message recovered by {@link OutageBackfillManager},
 * normalized from either the history service or a mirror source into a
 * common shape ready for rendering.
 *
 * @author tduva
 */
public class OutageMessage {

    public final String command;
    public final String userName;
    public final String message;
    public final boolean action;
    public final MsgTags tags;
    public final long timestampMs;

    public OutageMessage(String command, String userName, String message, boolean action,
            MsgTags tags, long timestampMs) {
        this.command = command;
        this.userName = userName;
        this.message = message;
        this.action = action;
        this.tags = tags;
        this.timestampMs = timestampMs;
    }

}
