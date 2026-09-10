
package chatty.gui.components.updating;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for stripCredentialArgs(): a token-value-token[space]token
 * shaped array (matching how MiscUtil.parseArgs actually joins multi-word
 * values, not a strict alternating "-key","value" shape) must have the
 * whole credential value removed, not just the first token of it, and must
 * not eat an unrelated following flag when the credential key has no value.
 */
public class RunUpdaterTest {

    @Test
    public void testNoCredentials_unchanged() {
        assertStripped(
                new String[]{"-channel", "somechannel", "-connect"},
                "-channel", "somechannel", "-connect");
    }

    @Test
    public void testSingleWordToken_removed() {
        assertStripped(
                new String[]{"-token", "abc123", "-channel", "somechannel"},
                "-channel", "somechannel");
    }

    @Test
    public void testMultiWordValue_fullyRemoved() {
        // MiscUtil.parseArgs joins consecutive non-"-" tokens into one
        // value, so an unquoted multi-word password is exactly this shape.
        assertStripped(
                new String[]{"-password", "my", "secret", "-connect"},
                "-connect");
    }

    @Test
    public void testCredentialKeyWithNoValue_doesNotEatNextFlag() {
        assertStripped(
                new String[]{"-token", "-connect"},
                "-connect");
    }

    @Test
    public void testSetTokenAndSetPassword_removed() {
        assertStripped(
                new String[]{"-set:token", "abc", "-set:password", "my", "pw", "-single"},
                "-single");
    }

    private static void assertStripped(String[] input, String... expected) {
        List<String> result = RunUpdater.stripCredentialArgs(input);
        assertEquals(Arrays.asList(expected), result);
    }

}
