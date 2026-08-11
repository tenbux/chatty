
package chatty;

import chatty.gui.emoji.EmojiUtil;
import chatty.util.Debugging;
import chatty.util.SpecialMap;
import chatty.util.api.SendMessageResult;
import chatty.util.history.QueuedMessage;
import chatty.util.irc.MsgTags;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
 * 2. Once the request completes, the real msg-id is assigned to the optimistic
 * line (see updateMsgIdForTempId) before any queued messages are passed to
 * printMessage, so the echo can be recognized and suppressed by id
 * (hasMsgId) instead of comparing text; messages from other clients are
 * shown normally
 *
 * @author tduva
 */
public class SendMessageManager {

    /**
     * The subset of TwitchApi this class needs, so it can be unit tested
     * without a real TwitchApi instance. TwitchApi.sendChatMessage already
     * matches this signature, so no adapter is needed at the call site.
     */
    public interface MessageSender {
        void sendChatMessage(String channelId, String message, String replyToMsgId, Consumer<SendMessageResult> listener);
    }

    /**
     * The subset of MainGui this class needs, so it can be unit tested
     * without a real MainGui instance.
     */
    public interface Output {
        long getEmojiZWJSetting();
        void printLine(String message);
        void printInfo(String channel, String message, MsgTags tags);
        void printMessage(User user, String text, boolean action, MsgTags tags);
        void updateMsgIdForTempId(String channel, String tempMsgId, String newMsgId);
    }

    private record ResendData(String channel, String text, String replyToMsgId) {}

    private int sentMessageId = 0;
    private final SpecialMap<String, Set<String>> sentMessagePending = new SpecialMap<>(new HashMap<>(), HashSet::new);
    private final Set<String> ignoreByMsgId = new HashSet<>();
    private final Map<String, ResendData> resendable = new ConcurrentHashMap<>();

    private final MessageSender api;
    private final Output g;

    private final Object LOCK = new Object();

    private final SpecialMap<String, List<QueuedMessage>> queuedMessages = new SpecialMap<>(new HashMap<>(), ArrayList::new);

    public SendMessageManager(MessageSender api, Output g) {
        this.api = api;
        this.g = g;
    }

    public String sendApiMessage(String channel, String text, String replyToMsgId, boolean action) {
        if (g.getEmojiZWJSetting() == 2) {
            text = EmojiUtil.encodeZWJ(text);
        }
        if (action) {
            text = (char)1+"ACTION "+text+(char)1;
        }
        return attemptSend(channel, text, replyToMsgId);
    }

    /**
     * Resend a message that previously failed with an uncertain
     * (connection-level) error, triggered by the user clicking the "Resend"
     * link shown next to that error.
     *
     * @param tempMsgId The id of the failed send attempt, as passed to the
     *                  RESEND link's target
     */
    public void resend(String tempMsgId) {
        ResendData data = resendable.remove(tempMsgId);
        if (data != null) {
            attemptSend(data.channel(), data.text(), data.replyToMsgId());
        }
    }

    private String attemptSend(String channel, String text, String replyToMsgId) {
        String tempMsgId;
        synchronized (LOCK) {
            tempMsgId = String.valueOf(sentMessageId);
            sentMessageId++;
            sentMessagePending.getPut(channel).add(tempMsgId);
        }
        api.sendChatMessage(Helper.toStream(channel), text, replyToMsgId, result -> {
                        if (!result.wasSent) {
                            if (result.uncertain) {
                                resendable.put(tempMsgId, new ResendData(channel, text, replyToMsgId));
                                g.printInfo(channel, "Message not sent: " + result.dropReasonMessage,
                                        MsgTags.createLinks(new MsgTags.Link(MsgTags.Link.Type.RESEND, tempMsgId, "Resend")));
                            }
                            else {
                                g.printLine("# Message not sent: " + result.dropReasonMessage);
                            }
                        }
                        synchronized (LOCK) {
                            sentMessagePending.getOptional(channel).remove(tempMsgId);
                            sentMessagePending.removeEmptyValues();
                            if (result.wasSent && result.msgId != null) {
                                ignoreByMsgId.add(result.msgId);
                                Debugging.println("sendmsg", "Will prioritize in queue: %s", result.msgId);
                                g.updateMsgIdForTempId(channel, tempMsgId, result.msgId);
                            }
                        }
                        handleQueuedMessages(channel);
                    });

        return tempMsgId;
    }

    /**
     * Process messages queued during a pending API request. The optimistic
     * line was already tagged with the real msg-id in sendApiMessage's
     * callback (before this is called), so our own echo (the one matching
     * ignoreByMsgId) is simply suppressed by id when printed; messages from
     * other clients are shown normally.
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
            // The optimistic line was already tagged with the real msg-id in
            // sendApiMessage's callback, so the echo is suppressed by id when printed.
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
