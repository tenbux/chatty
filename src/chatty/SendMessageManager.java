
package chatty;

import chatty.gui.MainGui;
import chatty.gui.emoji.EmojiUtil;
import chatty.util.Debugging;
import chatty.util.SpecialMap;
import chatty.util.api.TwitchApi;
import chatty.util.history.QueuedMessage;
import chatty.util.irc.MsgTags;

import java.util.*;

/**
 * Sending message through the API causes a message to be received back.
 * Displaying that message would cause a small delay between the user action and
 * the message appearing. Instead, the message is output immediately as before,
 * with the received message then not being shown.
 *
 * Since the Message ID is received from the API request only after a small
 * delay, and it may not be guaranteed that it is received before the chat
 * message is received, deduplication requires a two step process:
 * 1. Queuing all received local user messages while a request is pending
 * 2. Once the request completes, passing queued messages to printMessage, which
 * uses updateMsgIdForRecentMessage to assign the real msg-id to the optimistic
 * line and suppress the duplicate; messages from other clients are shown normally
 *
 * @author tduva
 */
public class SendMessageManager {

    private int sentMessageId = 0;
    private final SpecialMap<String, Set<String>> sentMessagePending = new SpecialMap<>(new HashMap<>(), HashSet::new);
    private final Set<String> ignoreByMsgId = new HashSet<>();
    
    private final TwitchApi api;
    private final MainGui g;
    
    private final Object LOCK = new Object();
    
    private final SpecialMap<String, List<QueuedMessage>> queuedMessages = new SpecialMap<>(new HashMap<>(), ArrayList::new);
    
    public SendMessageManager(TwitchApi api, MainGui g) {
        this.api = api;
        this.g = g;
    }
    
    public String sendApiMessage(String channel, String text, String replyToMsgId, boolean action) {
        if (g.getSettings().getLong("emojiZWJ") == 2) {
            text = EmojiUtil.encodeZWJ(text);
        }
        if (action) {
            text = (char)1+"ACTION "+text+(char)1;
        }
        String tempMsgId;
        synchronized (LOCK) {
            tempMsgId = String.valueOf(sentMessageId);
            sentMessageId++;
            sentMessagePending.getPut(channel).add(tempMsgId);
        }
        api.sendChatMessage(Helper.toStream(channel), text, replyToMsgId, result -> {
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException ex) {
//                Logger.getLogger(SendMessageManager.class.getName()).log(Level.SEVERE, null, ex);
//            }
                        if (!result.wasSent) {
                            g.printLine("# Message not sent: " + result.dropReasonMessage);
                        }
                        synchronized (LOCK) {
                            sentMessagePending.getOptional(channel).remove(tempMsgId);
                            sentMessagePending.removeEmptyValues();
                            if (result.wasSent && result.msgId != null) {
                                ignoreByMsgId.add(result.msgId);
                                Debugging.println("sendmsg", "Will prioritize in queue: %s", result.msgId);
                            }
                        }
                        handleQueuedMessages(channel);
                    });
        
        return tempMsgId;
    }
    
    /**
     * Process messages queued during a pending API request. Our own echo (the
     * one matching ignoreByMsgId) is dispatched first so that
     * updateMsgIdForRecentMessage assigns the real msg-id to the optimistic line
     * before any messages from other clients are processed. That way a
     * concurrent website message cannot be mistakenly matched to the orphaned
     * optimistic line and swallowed.
     *
     * @param channel
     */
    private void handleQueuedMessages(String channel) {
        synchronized (LOCK) {
            debugStatus("sendQueued");
            if (sentMessagePending.containsKey(channel)) {
                return;
            }
            if (!queuedMessages.containsKey(channel)) {
                return;
            }
        }
        List<QueuedMessage> ours = new ArrayList<>();
        List<QueuedMessage> others = new ArrayList<>();
        synchronized (LOCK) {
            for (QueuedMessage msg : queuedMessages.getOptional(channel)) {
                if (ignoreByMsgId.remove(msg.tags().getId())) {
                    ours.add(msg);
                } else {
                    others.add(msg);
                }
            }
            queuedMessages.remove(channel);
        }
        for (QueuedMessage msg : ours) {
            g.printMessage(msg.user(), msg.text(), msg.action(), msg.tags());
        }
        for (QueuedMessage msg : others) {
            g.printMessage(msg.user(), msg.text(), msg.action(), msg.tags());
        }
    }
    
    /**
     * Check if the given message should be ignored (should have already checked
     * that it is indeed a local user message). The message may be added to a
     * queue to be output later if the msg id hasn't been received yet.
     * 
     * @param user
     * @param text
     * @param tags
     * @param action
     * @return 
     */
    public boolean shouldIgnoreMessage(User user, String text, MsgTags tags, boolean action) {
        synchronized (LOCK) {
            debugStatus("shouldIgnore");
            // Clean up if this echo arrives after sentMessagePending cleared (not via queue).
            // The echo still flows to g.printMessage so updateMsgIdForRecentMessage can
            // assign the real msg-id to the optimistic line and suppress the duplicate.
            ignoreByMsgId.remove(tags.getId());
            if (sentMessagePending.containsKey(user.getChannel())) {
                Debugging.println("sendMsg", "Ignored for now (messages pending): %s", text);
                queuedMessages.getPut(user.getChannel()).add(
                        new QueuedMessage(user, text, action, tags));
                return true;
            }
            return false;
        }
    }
    
    private void debugStatus(String where) {
        Debugging.println("sendMsg", "[%s] Temp: %s Ignore: %s Queue: %s", where, sentMessagePending, ignoreByMsgId, queuedMessages);
    }
    
}
