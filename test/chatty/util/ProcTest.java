
package chatty.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 *
 * @author tduva
 */
public class ProcTest {
    
    @Test
    public void testSplit() {
        assertArrayEquals(new String[]{}, Proc.split(""));
        assertArrayEquals(new String[]{"a"}, Proc.split("a"));
        assertArrayEquals(new String[]{"\\a"}, Proc.split("\\a"));
        assertArrayEquals(new String[]{"a"}, Proc.split("\"a"));
        assertArrayEquals(new String[]{"a b cd"}, Proc.split("\"a b cd\""));
        assertArrayEquals(new String[]{"a", "b", "cd"}, Proc.split("a b cd"));
        assertArrayEquals(new String[]{"a \"b\" cd"}, Proc.split("\"a \\\"b\\\" cd\""));
        assertArrayEquals(new String[]{}, Proc.split("\"\""));
        assertArrayEquals(new String[]{" "}, Proc.split("\" \""));
        assertArrayEquals(new String[]{"a \\b\\ c"}, Proc.split("\"a \\b\\ c\""));              // "a \b\ c" -> a \b\ c
        assertArrayEquals(new String[]{"\"a\"","b"}, Proc.split("\\\"a\\\" b"));                // \"a\" b -> "a",b
        assertArrayEquals(new String[]{"\"a","b\""}, Proc.split("\\\"a b\\\""));                // \"a b\" -> "a,b"
        assertArrayEquals(new String[]{"1 2 3\\"}, Proc.split("\"1 2 3\\\""));                  // "1 2 3\" -> 1 2 3\
        assertArrayEquals(new String[]{"1 2 3\\\\"}, Proc.split("\"1 2 3\\\\\""));              // "1 2 3\\" -> 1 2 3\\
        assertArrayEquals(new String[]{"1 2 3\\", "abc"}, Proc.split("\"1 2 3\\\" abc"));       // "1 2 3\" abc -> 1 2 3\,abc (last quote is used as closing quote)
        assertArrayEquals(new String[]{"1 2 3\" abc"}, Proc.split("\"1 2 3\\\" abc\""));        // "1 2 3\" abc" -> 1 2 3" abc
        assertArrayEquals(new String[]{"1 2 3\" a ","b","c"}, Proc.split("\"1 2 3\\\" a \"b c\"")); // "1 2 3\" a "b c" -> 1 2 3" a ,b,c
        assertArrayEquals(new String[]{"1 2 3\\\\", "abc"}, Proc.split("\"1 2 3\\\\\" abc"));   // "1 2 3\\" abc -> 1 2 3 \\,abc
        assertArrayEquals(new String[]{"1 2 3\\\""}, Proc.split("\"1 2 3\\\\\"\""));            // "1 2 3\\"" -> 1 2 3\"
    }
    
}
