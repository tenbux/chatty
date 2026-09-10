
package chatty.util.commands;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Regression tests for two chat-text-crashes-the-interpreter bugs:
 *
 * - $round(): Double.parseDouble("NaN"/"Infinity") succeeds (doesn't throw
 *   NumberFormatException), but BigDecimal.valueOf() a few lines later in
 *   Round.round() throws for either, uncaught at that point. A chat-derived
 *   argument of "NaN"/"Infinity"/"-Infinity" reaching $round(...) crashed
 *   the interpreter instead of falling back to the original string like
 *   other unparseable input already does.
 *
 * - $join(): replaceAll(" ", sep) treats "$"/"\" in the replacement
 *   specially; sep is chat-derived/parameter text and could contain
 *   either, throwing instead of joining.
 */
public class RoundJoinTest {

    @Test
    public void testRound_nan_fallsBackToOriginalString() {
        assertEquals("NaN", CustomCommand.parse("$round($1)").replace(Parameters.create("NaN")));
    }

    @Test
    public void testRound_infinity_fallsBackToOriginalString() {
        assertEquals("Infinity", CustomCommand.parse("$round($1)").replace(Parameters.create("Infinity")));
    }

    @Test
    public void testRound_negativeInfinity_fallsBackToOriginalString() {
        assertEquals("-Infinity", CustomCommand.parse("$round($1)").replace(Parameters.create("-Infinity")));
    }

    @Test
    public void testRound_normalNumber_stillRounds() {
        assertEquals("3.14", CustomCommand.parse("$round($1,2)").replace(Parameters.create("3.14159")));
    }

    @Test
    public void testJoin_dollarSeparator_doesNotThrow() {
        // "$2" resolves to the literal string "$" here; the old
        // replaceAll(" ", sep) treated a bare "$" as an (invalid) group
        // reference and threw IllegalArgumentException.
        String result = CustomCommand.parse("$join($1-,$2)").replace(Parameters.create("hello $ world"));
        assertEquals("hello$$$world", result);
    }

    @Test
    public void testJoin_backslashSeparator_doesNotThrow() {
        String result = CustomCommand.parse("$join($1-,$2)").replace(Parameters.create("hello \\ world"));
        assertEquals("hello\\\\\\world", result);
    }

    @Test
    public void testJoin_normalSeparator_stillJoins() {
        String result = CustomCommand.parse("$join($1-,$2)").replace(Parameters.create("hello , world"));
        assertEquals("hello,,,world", result);
    }

}
