
package chatty.gui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for the crash-loop bug in MainGui#error(): an invalid
 * "ignoreError" regex used to throw PatternSyntaxException from inside the
 * app's own uncaught-exception-handler chain, which re-triggered error()
 * and looped until a hardcoded error-rate safety counter force-killed the
 * process. matchesIgnoreError() must never throw, regardless of pattern.
 */
public class MainGuiTest {

    @Test
    public void testInvalidPattern_doesNotThrow_treatedAsNoMatch() {
        // This is the actual bug: "(" is not a valid regex and used to
        // propagate PatternSyntaxException straight out of error().
        assertFalse(MainGui.matchesIgnoreError("(", "some error text"));
        assertFalse(MainGui.matchesIgnoreError("[", "some error text"));
        assertFalse(MainGui.matchesIgnoreError("*invalid", "some error text"));
    }

    @Test
    public void testEmptyOrNullPattern_neverMatches() {
        assertFalse(MainGui.matchesIgnoreError("", "some error text"));
        assertFalse(MainGui.matchesIgnoreError(null, "some error text"));
    }

    @Test
    public void testValidPattern_matchesAsExpected() {
        assertTrue(MainGui.matchesIgnoreError("SocketTimeoutException", "java.net.SocketTimeoutException: connect timed out"));
        assertFalse(MainGui.matchesIgnoreError("SocketTimeoutException", "java.lang.NullPointerException"));
    }

}
