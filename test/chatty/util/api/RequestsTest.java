
package chatty.util.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Regression test for Requests#filterToken(): it must redact a token
 * wherever it appears in a string, including inside an exception message
 * that embeds a full URL (e.g. "Server returned HTTP response code: 400
 * for URL: https://id.twitch.tv/oauth2/revoke?client_id=...&amp;token=&lt;token&gt;"),
 * not just when filtering a bare URL.
 */
public class RequestsTest {

    @Test
    public void testFilterToken_url() {
        String url = "https://api.twitch.tv/helix/users?login=x&token=abc123secret";
        assertEquals("https://api.twitch.tv/helix/users?login=x&token=<token>",
                Requests.filterToken(url, "abc123secret"));
    }

    @Test
    public void testFilterToken_exceptionMessageEmbeddingUrl() {
        // This is the actual shape of the bug: a non-2xx response's
        // IOException.toString() embeds the full request URL, including a
        // token that was only ever meant to be in the URL because
        // revokeToken() puts it in the query string.
        String error = "java.io.IOException: Server returned HTTP response code: 400 for URL: "
                + "https://id.twitch.tv/oauth2/revoke?client_id=xyz&token=abc123secret";
        String filtered = Requests.filterToken(error, "abc123secret");
        assertFalse("token must not appear anywhere in the filtered text",
                filtered.contains("abc123secret"));
        assertEquals("java.io.IOException: Server returned HTTP response code: 400 for URL: "
                + "https://id.twitch.tv/oauth2/revoke?client_id=xyz&token=<token>", filtered);
    }

    @Test
    public void testFilterToken_nullOrEmptyToken_returnsInputUnchanged() {
        assertEquals("some text", Requests.filterToken("some text", null));
        assertEquals("some text", Requests.filterToken("some text", ""));
    }

    @Test
    public void testFilterToken_nullInput_returnsNull() {
        assertEquals(null, Requests.filterToken(null, "abc"));
    }

}
