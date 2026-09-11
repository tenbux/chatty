
package chatty.util.commands;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Regression test for IfEq.equals(): every field comparison was inverted
 * (returning false when the fields WERE equal), so it broke reflexivity
 * (a.equals(a) was false) and made CustomCommand.equals() (which compares
 * the parsed item list) unusable for any command containing $ifeq(...).
 */
public class IfEqTest {

    @Test
    public void testEquals_isReflexive() {
        CustomCommand cmd = CustomCommand.parse("$ifeq($1,x,yes,no)");
        assertEquals(cmd, cmd);
    }

    @Test
    public void testEquals_identicalCommandsParsedSeparately_areEqual() {
        CustomCommand cmd1 = CustomCommand.parse("$ifeq($1,x,yes,no)");
        CustomCommand cmd2 = CustomCommand.parse("$ifeq($1,x,yes,no)");
        assertEquals(cmd1, cmd2);
        assertEquals(cmd1.hashCode(), cmd2.hashCode());
    }

    @Test
    public void testEquals_differentOutput2_notEqual() {
        CustomCommand cmd1 = CustomCommand.parse("$ifeq($1,x,yes,no)");
        CustomCommand cmd2 = CustomCommand.parse("$ifeq($1,x,yes,NO)");
        assertNotEquals(cmd1, cmd2);
    }

    @Test
    public void testEquals_differentCompareValue_notEqual() {
        CustomCommand cmd1 = CustomCommand.parse("$ifeq($1,x,yes,no)");
        CustomCommand cmd2 = CustomCommand.parse("$ifeq($1,y,yes,no)");
        assertNotEquals(cmd1, cmd2);
    }

}
