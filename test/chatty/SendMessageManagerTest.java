
package chatty;

import chatty.util.api.SendMessageResult;
import chatty.util.irc.MsgTags;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/**
 * Covers the own-message echo dedup logic in SendMessageManager, which has
 * needed several follow-up fixes for messages getting duplicated or swallowed
 * (see git history for SendMessageManager/ChannelTextPane).
 *
 * @author tduva
 */
public class SendMessageManagerTest {

    private static final String CHANNEL = "#channel";
    private static final User USER = new User("owner", Room.createRegular(CHANNEL));

    private FakeSender sender;
    private FakeOutput output;
    private SendMessageManager manager;

    @Before
    public void setup() {
        sender = new FakeSender();
        output = new FakeOutput();
        manager = new SendMessageManager(sender, output);
    }

    @Test
    public void ownEchoQueuedWhilePendingIsTaggedBeforeItPrints() {
        String tempMsgId = manager.sendApiMessage(CHANNEL, "hello", null, false);

        boolean ignored = manager.shouldIgnoreMessage(USER, "hello", MsgTags.create("id", "real-123"), false);
        assertTrue("Echo arriving while the send is still pending must be queued/ignored", ignored);
        assertTrue("Nothing should print until the send resolves", output.printedMessages.isEmpty());

        sender.respond(success("real-123"));

        assertEquals(List.of(CHANNEL + ":" + tempMsgId + "->real-123"), output.taggedTempIds);
        assertEquals(1, output.printedMessages.size());
        assertEquals("real-123", output.printedMessages.get(0).tags.getId());
        assertEquals("The optimistic line must be tagged with the real id before the queued echo is printed",
                List.of("tag", "print"), output.order);
    }

    @Test
    public void failedSendDoesNotTagAnythingAndReportsTheDropReason() {
        manager.sendApiMessage(CHANNEL, "hello", null, false);
        sender.respond(failure("rate limited"));

        assertTrue(output.taggedTempIds.isEmpty());
        assertEquals(1, output.lines.size());
        assertTrue(output.lines.get(0).contains("rate limited"));
    }

    @Test
    public void echoArrivingAfterSendAlreadyResolvedIsNotQueued() {
        manager.sendApiMessage(CHANNEL, "hello", null, false);
        sender.respond(success("real-123"));

        boolean ignored = manager.shouldIgnoreMessage(USER, "hello", MsgTags.create("id", "real-123"), false);
        assertFalse("Once the send resolved, the line is already tagged directly (no queueing needed)", ignored);
    }

    @Test
    public void ownNameEchoWithNoPendingSendIsNeverQueued() {
        boolean ignored = manager.shouldIgnoreMessage(USER, "hi from the website", MsgTags.create("id", "website-1"), false);
        assertFalse(ignored);
    }

    @Test
    public void unrelatedEchoQueuedAlongsideAPendingSendIsPrintedNormally() {
        String tempMsgId = manager.sendApiMessage(CHANNEL, "hello", null, false);

        // e.g. a message sent from the Twitch website by the same user, arriving
        // while our own send is still pending
        boolean ignored = manager.shouldIgnoreMessage(USER, "hi from the website", MsgTags.create("id", "website-1"), false);
        assertTrue(ignored);

        sender.respond(success("real-123"));

        assertEquals(List.of(CHANNEL + ":" + tempMsgId + "->real-123"), output.taggedTempIds);
        assertEquals("The unrelated message must not be swallowed by our own tagging",
                1, output.printedMessages.size());
        assertEquals("website-1", output.printedMessages.get(0).tags.getId());
        assertEquals("hi from the website", output.printedMessages.get(0).text);
    }

    @Test
    public void uncertainFailurePrintsResendLinkInsteadOfPlainLine() {
        manager.sendApiMessage(CHANNEL, "hello", null, false);
        sender.respond(SendMessageResult.connectionFailure("timed out"));

        assertTrue("A known failure reason should never be shown as a plain line for an uncertain result",
                output.lines.isEmpty());
        assertEquals(1, output.infoMessages.size());
        assertTrue(output.infoMessages.get(0).contains("timed out"));
    }

    @Test
    public void resendingAfterUncertainFailureSendsAgainAndCanSucceed() {
        String tempMsgId = manager.sendApiMessage(CHANNEL, "hello", null, false);
        sender.respond(SendMessageResult.connectionFailure("timed out"));

        manager.resend(tempMsgId);
        sender.respond(success("real-123"));

        assertEquals("Resend must retry with the original message text",
                "hello", sender.lastMessage);
        assertEquals("The resend attempt must be tagged with the real id once it succeeds",
                1, output.taggedTempIds.size());
        assertTrue(output.taggedTempIds.get(0).endsWith("->real-123"));
    }

    @Test
    public void resendingUnknownTempMsgIdDoesNothing() {
        manager.resend("does-not-exist");
        assertNull(sender.lastListener);
    }

    private static SendMessageResult success(String msgId) {
        return SendMessageResult.parse("{\"data\":[{\"message_id\":\"" + msgId + "\",\"is_sent\":true}]}");
    }

    private static SendMessageResult failure(String dropReasonMessage) {
        return SendMessageResult.parse("{\"data\":[{\"is_sent\":false,\"drop_reason\":{\"code\":\"x\",\"message\":\""
                + dropReasonMessage + "\"}}]}");
    }

    private static class FakeSender implements SendMessageManager.MessageSender {

        private Consumer<SendMessageResult> lastListener;
        private String lastMessage;

        @Override
        public void sendChatMessage(String channelId, String message, String replyToMsgId, Consumer<SendMessageResult> listener) {
            lastListener = listener;
            lastMessage = message;
        }

        void respond(SendMessageResult result) {
            lastListener.accept(result);
        }
    }

    private static class FakeOutput implements SendMessageManager.Output {

        final List<String> lines = new ArrayList<>();
        final List<String> infoMessages = new ArrayList<>();
        final List<PrintedMessage> printedMessages = new ArrayList<>();
        final List<String> taggedTempIds = new ArrayList<>();
        final List<String> order = new ArrayList<>();

        @Override
        public long getEmojiZWJSetting() {
            return 0;
        }

        @Override
        public void printLine(String message) {
            lines.add(message);
        }

        @Override
        public void printInfo(String channel, String message, MsgTags tags) {
            infoMessages.add(message);
        }

        @Override
        public void printMessage(User user, String text, boolean action, MsgTags tags) {
            printedMessages.add(new PrintedMessage(user, text, action, tags));
            order.add("print");
        }

        @Override
        public void updateMsgIdForTempId(String channel, String tempMsgId, String newMsgId) {
            taggedTempIds.add(channel + ":" + tempMsgId + "->" + newMsgId);
            order.add("tag");
        }
    }

    private static class PrintedMessage {

        final User user;
        final String text;
        final boolean action;
        final MsgTags tags;

        PrintedMessage(User user, String text, boolean action, MsgTags tags) {
            this.user = user;
            this.text = text;
            this.action = action;
            this.tags = tags;
        }
    }

}
