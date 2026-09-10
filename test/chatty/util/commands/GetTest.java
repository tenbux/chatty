
package chatty.util.commands;

import chatty.util.settings.Settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Regression test for the $get() credential-exfiltration fix: a custom
 * command must never be able to read out the real "token"/"password"
 * setting value, since $request() can send it to an arbitrary URL.
 */
public class GetTest {

    private static Settings makeSettings() {
        Settings settings = new Settings(null, null);
        settings.addString("token", "");
        settings.addString("password", "");
        settings.addString("username", "");
        settings.setString("token", "super-secret-oauth-token");
        settings.setString("password", "super-secret-irc-password");
        settings.setString("username", "someuser");
        return settings;
    }

    @Test
    public void testGetToken_neverReturnsRealValue() {
        assertEquals("", get("$get(token)"));
    }

    @Test
    public void testGetPassword_neverReturnsRealValue() {
        assertEquals("", get("$get(password)"));
    }

    @Test
    public void testGetOrdinarySetting_stillWorks() {
        // Non-sensitive settings must still work normally, otherwise this
        // fix would be too broad.
        assertEquals("someuser", get("$get(username)"));
    }

    @Test
    public void testGetToken_requiredVariant_returnsNullNotRealValue() {
        // $$get(token) is the "required" form (null propagates up and
        // aborts the command instead of substituting ""); it must not
        // leak the token either.
        String result = getRaw("$$get(token)");
        assertFalse("must not equal the real token value",
                "super-secret-oauth-token".equals(result));
    }

    private static String get(String command) {
        Parameters parameters = Parameters.create("");
        parameters.putObject("settings", makeSettings());
        String result = CustomCommand.parse(command).replace(parameters);
        return result == null ? "" : result;
    }

    private static String getRaw(String command) {
        Parameters parameters = Parameters.create("");
        parameters.putObject("settings", makeSettings());
        return CustomCommand.parse(command).replace(parameters);
    }

}
