package chatty.util.api;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PinnedMessageTest {

    private static final long NOW = 1_000_000L;

    private static PinnedMessage pinned(long endsAt) {
        return new PinnedMessage("stream", "msg-id", "123", "sender", "pinner", "Hello chat", endsAt);
    }

    @Test
    public void test_isActive_untilEndOfStream_returnsTrue() {
        assertTrue(pinned(-1).isActive(NOW));
    }

    @Test
    public void test_isActive_endsInFuture_returnsTrue() {
        assertTrue(pinned(NOW + 1).isActive(NOW));
    }

    @Test
    public void test_isActive_endTimeEqualsNow_returnsFalse() {
        assertFalse(pinned(NOW).isActive(NOW));
    }

    @Test
    public void test_isActive_endTimeInPast_returnsFalse() {
        assertFalse(pinned(NOW - 1).isActive(NOW));
    }

    @Test
    public void test_isActive_noPinnedMessage_returnsFalse() {
        assertFalse(PinnedMessage.EMPTY.isActive(NOW));
    }

    @Test
    public void test_isActive_requestError_returnsFalse() {
        assertFalse(new PinnedMessage("Request error code 403").isActive(NOW));
    }

    @Test
    public void test_isActive_parsedResponseWithNullEndsAt_returnsTrue() {
        String json = "{\"data\":[{\"message_id\":\"m\",\"broadcaster_id\":\"1\",\"sender_user_login\":\"s\","
                + "\"pinned_by_user_login\":\"p\",\"message\":{\"text\":\"Hi\"},\"ends_at\":null}]}";
        assertTrue(PinnedMessage.parse("stream", json).isActive(NOW));
    }

    @Test
    public void test_isActive_parsedEmptyData_returnsFalse() {
        assertFalse(PinnedMessage.parse("stream", "{\"data\":[]}").isActive(NOW));
    }
}
