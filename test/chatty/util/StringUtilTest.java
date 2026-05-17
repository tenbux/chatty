
package chatty.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

/**
 *
 * @author tduva
 */
public class StringUtilTest {
    
    @Test
    public void testRemoveLinebreakCharacters() {
        assertEquals("abc abc", StringUtil.removeLinebreakCharacters("abc\r\nabc"));
        assertEquals("abc abc", StringUtil.removeLinebreakCharacters("abc\rabc"));
        assertEquals("abc abc", StringUtil.removeLinebreakCharacters("abc\nabc"));
        assertEquals("abc abc", StringUtil.removeLinebreakCharacters("abc abc"));
        assertEquals("abc abc", StringUtil.removeLinebreakCharacters("abc\r\r\r\r\rabc"));
        assertEquals("abc abc", StringUtil.removeLinebreakCharacters("abc\r\n\n\r\rabc"));
        assertEquals(" abc abc", StringUtil.removeLinebreakCharacters("\nabc abc"));
        assertEquals(" ", StringUtil.removeLinebreakCharacters("\r"));
    }
    
    @Test
    public void testRemoveDuplicateWhitespace() {
        assertEquals("abc abc", StringUtil.removeDuplicateWhitespace("abc  abc"));
        assertEquals("abc abc", StringUtil.removeDuplicateWhitespace("abc   abc"));
        assertEquals("abcabc", StringUtil.removeDuplicateWhitespace("abcabc"));
        assertEquals("abc abc", StringUtil.removeDuplicateWhitespace("abc abc"));
        assertEquals(" ", StringUtil.removeDuplicateWhitespace("  "));
        assertEquals("", StringUtil.removeDuplicateWhitespace(""));
    }
    
    @Test
    public void testAppend() {
        assertEquals("abc|abc", StringUtil.append("abc", "|", "abc"));
        assertEquals("abcabc", StringUtil.append("abc", "", "abc"));
        assertEquals("b", StringUtil.append(null, "|", "b"));
        assertEquals("b", StringUtil.append("", "|", "b"));
        assertEquals("abc", StringUtil.append("abc", "|", null));
        assertEquals("abcnullabc", StringUtil.append("abc", null, "abc"));
        assertNull(StringUtil.append(null, null, null));
    }
    
    @Test
    public void testJoin() {
        Collection<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        list.add("c");
        assertEquals("a b c", StringUtil.join(list, " "));
        assertEquals("a, b, c", StringUtil.join(list, ", "));
        assertEquals("b, c", StringUtil.join(list, ", ", 1));
        assertEquals("a, b", StringUtil.join(list, ", ", 0, 2));
        assertEquals("a, b", StringUtil.join(list, ", ", -1, 2));
        assertEquals("a, b", StringUtil.join(list, ", ", -10, 2));
        assertEquals("b", StringUtil.join(list, ", ", 1, 2));
        assertEquals("", StringUtil.join(list, ", ", 10, 2));
        assertEquals("a-b-c", StringUtil.join(list, "-", 0, 100));
        assertEquals("c", StringUtil.join(list, ", ", 2));
        assertEquals("", StringUtil.join(list, ", ", 3));
        list.add(" d");
        assertEquals("a, b, c,  d", StringUtil.join(list, ", "));
    }
    
    @Test
    public void testFirstToUpperCase() {
        assertEquals("", StringUtil.firstToUpperCase(""));
        assertEquals("A", StringUtil.firstToUpperCase("a"));
        assertNull(StringUtil.firstToUpperCase(null));
        assertEquals("Abc", StringUtil.firstToUpperCase("Abc"));
        assertEquals("Abc", StringUtil.firstToUpperCase("abc"));
        assertEquals(" abc", StringUtil.firstToUpperCase(" abc"));
        
    }
    
    @Test
    public void testSplit() {
        assertNull(StringUtil.split(null, 'a', 10));
        testSplit2(',', 0, "", "");
        testSplit2(',', 0, "a\\,b,c", "a,b", "c");
        testSplit2(',', 0, "a\\\\,b,c", "a\\", "b", "c");
        testSplit2(',', 0, "abc", "abc");
        testSplit2(',', 0, "\\abc", "abc");
        testSplit2(',', 0, "\\\\abc", "\\abc");
        testSplit2(',', 0, "a,b,c", "a", "b", "c");
        testSplit2(',', 1, "a,b,c", "a,b,c");
        testSplit2(',', 2, "a,b,c", "a", "b,c");
        testSplit2(',', 0, "'a,b',c", "a,b", "c");
        testSplit2(',', 0, "\\'a,b,c", "'a", "b", "c");
        testSplit2(',', 2, "\\'a,b,c'", "'a", "b,c'");
        testSplit2(',', '\'', '\\', 2, 2, "\\'a,b,c'", "'a", "b,c");
        testSplit2(',', 2, "a,b,\\c", "a", "b,\\c");
        testSplit2(',', '\'', '\\', 2, 2, "a,b,\\c", "a", "b,c");
        testSplit2(',', 2, "a\\,b,c", "a,b", "c");
        testSplit2(',', 0, "t|test=abc\\,lol", "t|test=abc,lol");
        
        // Double quotes
        testSplit2(',', 0, "''", "");
        testSplit2(',', 0, "\\''", "'");
        
        // Test quote == escape
        testSplit2Same(',', 0, "'a,b',c", "a,b", "c");
        testSplit2Same(',', 0, "'a,b'',c", "a,b',c");
        testSplit2Same(',', 0, "''", "'");
        testSplit2Same(',', 0, "'''", "'");
        testSplit2Same(',', 0, "''''", "''");
        testSplit2Same(',', 0, "''a,b',c,d", "'a", "b,c,d");
        testSplit2Same(',', 0, "''a,b,',c,d", "'a", "b", ",c,d");
        testSplit2Same(',', 0, "'a,b''',c", "a,b'", "c");
        
        // First split by space, then by comma (test not removing quote/escape)
        testSplit2(',', 0, StringUtil.split("a,b,'c d' e", ' ', '\'', '\\', 2, 0).getFirst(), "a", "b", "c d");
        testSplit2(',', 0, StringUtil.split("a,b,c\\ d e", ' ', '\'', '\\', 2, 0).getFirst(), "a", "b", "c d");
        testSplit2(',', '\'', '\'', 0, 1, StringUtil.split("a,b,'c'' d' e", ' ', '\'', '\'', 2, 0).getFirst(), "a", "b", "c' d");
        testSplit2(',', '\'', '\'', 0, 1, StringUtil.split("''a,b,'c d,e'", ' ', '\'', '\'', 2, 0).getFirst(), "'a", "b", "c d,e");
        testSplit2(',', '\'', '\'', 0, 1, StringUtil.split("''a,b,'''c'' d,e'", ' ', '\'', '\'', 2, 0).getFirst(), "'a", "b", "'c' d,e");
        
        // Various configurations
        testSplit2(',', '#', '#', 2, 1, "a,b,c", "a", "b,c");
        testSplit2(',', '#', '#', 2, 1, "a#,b,c", "a,b,c");
        testSplit2(' ', '-', '$', 2, 1, "abc- -123 -b c-", "abc 123", "-b c-");
        testSplit2(' ', '-', '$', 2, 1, "abc- $-123 -b c-", "abc -123 b", "c-");
        testSplit2(' ', '-', '$', 2, 1, "abc$ 123 -b c-", "abc 123", "-b c-");
        testSplit2(' ', '-', '$', 3, 1, "abc$ 123 -b c-", "abc 123", "b c");
        testSplit2(' ', '-', '$', 0, 1, "abc$ 123 -b c-", "abc 123", "b c");
        testSplit2(' ', '-', '$', 2, 2, "abc$ 123 -b c-", "abc 123", "b c");
        testSplit2(' ', '-', '$', 2, 0, "abc$ 123 -b c-", "abc$ 123", "-b c-");
        testSplit2(' ', '-', '-', 0, 0, "abc 123 -b c-", "abc", "123", "-b c-");
        testSplit2(' ', '-', '-', 0, 0, "abc 123 -b-- c-", "abc", "123", "-b-- c-");
        testSplit2(' ', '-', '$', 0, 0, "abc$ 123 -b c-", "abc$ 123", "-b c-");
    }
    
    private static void testSplit2(char split, int limit, String input, String... result) {
        testSplit2(split, '\'', '\\', limit, 1, input, result);
    }
    
    private static void testSplit2Same(char split, int limit, String input, String... result) {
        testSplit2(split, '\'', '\'', limit, 1, input, result);
    }
    
    private static void testSplit2(char split, char quote, char escape, int limit, int remove, String input, String... result) {
        assertEquals(Arrays.asList(result), StringUtil.split(input, split, quote, escape, limit, remove));
    }
    
    @Test
    public void testSplitLines() {
        assertArrayEquals(new String[]{"a"}, StringUtil.splitLines("a"));
        assertArrayEquals(new String[]{"a","b"}, StringUtil.splitLines("a\nb"));
        assertArrayEquals(new String[]{"a","b"}, StringUtil.splitLines("a\rb"));
        assertArrayEquals(new String[]{"a","b"}, StringUtil.splitLines("a\r\nb"));
        assertArrayEquals(new String[]{"a","","b"}, StringUtil.splitLines("a\n\rb")); // Invalid linebreak
    }
    
    @Test
    public void testQuote() {
        assertEquals("\"abc\"", StringUtil.quote("abc"));
        assertEquals("'abc'", StringUtil.quote("abc", "'"));
        assertEquals("''", StringUtil.quote("", "'"));
        assertEquals("'O''Neill'", StringUtil.quote("O'Neill", "'"));
        assertEquals("''''", StringUtil.quote("'", "'"));
        assertEquals("+abc+", StringUtil.quote("abc", "+"));
    }
    
    @Test
    public void testReplaceFunc() {
        assertEquals("a b c ", StringUtil.replaceFunc("~abc~", "~([a-z]+)~", m -> m.group(1).replaceAll("([a-z])", "$1 ")));
    }
    
    @Test
    public void testSimilarity() {
        assertEquals(1, StringUtil.getSimilarity("", ""), 0);
        assertEquals(0, StringUtil.getSimilarity("a", ""), 0);
        assertEquals(0, StringUtil.getSimilarity("a", "b"), 0);
        assertEquals(0.6, StringUtil.getSimilarity("abc", "ab"), 0.1);
        assertEquals(0.8, StringUtil.getSimilarity("abcd", "abc"), 0.1);
        assertEquals(0.83, StringUtil.getSimilarity("This is a longer message", "This is a message that's longer"), 0.1);
        assertEquals(0.25, StringUtil.getSimilarity("night", "nacht"), 0.01);
        assertEquals(0.25, StringUtil.getSimilarity2("night", "nacht"), 0.01);
        assertEquals(0.5, StringUtil.getSimilarity("aa", "aaaa"), 0.01);
        assertEquals(1, StringUtil.getSimilarity2("aa", "aaaa"), 0.01);
        
        assertEquals(1, StringUtil.checkSimilarity("", "", 0, 1), 0);
        assertEquals(1, StringUtil.checkSimilarity("", "", 1, 1), 0);
        assertEquals(0, StringUtil.checkSimilarity("a", "", 0, 1), 0);
        assertEquals(0, StringUtil.checkSimilarity("a", "", 0.1f, 1), 0);
        
        assertEquals(0, StringUtil.checkSimilarity("aa", "aaaa", 0.6f, 1), 0.1);
        assertEquals(0.5, StringUtil.checkSimilarity("aa", "aaaa", 0.5f, 1), 0.01);
        assertEquals(1, StringUtil.checkSimilarity("aa", "aaaa", 0.6f, 2), 0.1);
        
        assertTrue(StringUtil.checkSimilarity("This is a longer message", "This is a message that's longer", 0.8f, 1) > 0);
    }
    
    @Test
    public void testRemoveWhitespaceAndMore() {
        assertEquals("", StringUtil.removeWhitespaceAndMore("!!", new char[]{'!'}));
        assertEquals("abc", StringUtil.removeWhitespaceAndMore("abc!", new char[]{'!'}));
        assertEquals("abc↗", StringUtil.removeWhitespaceAndMore("!abc!↗", new char[]{'!'}));
        assertEquals("abc", StringUtil.removeWhitespaceAndMore("!abc!↗", new char[]{'!', '↗'}));
        assertEquals("🐱", StringUtil.removeWhitespaceAndMore("!🐱!↗", new char[]{'!', '↗'}));
        // Array not sorted correctly, result is undefined, so not sure if this test works
//        assertEquals("!🐱!", StringUtil.removeWhitespaceAndMore("!🐱!↗", new char[]{'↗', '!'}));
        
        /**
         * Specifying a surrogate character directly would remove it, but most
         * of the time that would probably not be what is wanted.
         * 
         * StringUtil.getCharsFromString() can be used to ignore surrogates,
         * which excludes all chracters outside the BMP. This can be ok if the
         * use-case doesn't require it.
         */
        assertEquals("🐱", StringUtil.removeWhitespaceAndMore("!🐱!↗", StringUtil.getCharsFromString("!↗")));
        assertEquals("🐱", StringUtil.removeWhitespaceAndMore("!🐱!↗", StringUtil.getCharsFromString("↗!")));
        assertEquals("🐱", StringUtil.removeWhitespaceAndMore("!🐱!↗", StringUtil.getCharsFromString("↗!🐱abc")));
        assertEquals("!🐱!↗", StringUtil.removeWhitespaceAndMore("!🐱!↗", StringUtil.getCharsFromString("🐱")));
        assertEquals("!🐱!↗", StringUtil.removeWhitespaceAndMore("!🐱!↗", StringUtil.getCharsFromString("\uD83D")));
        // Surrogate directly specified, leaves the other surrogate
        assertEquals("!\uDC31!↗", StringUtil.removeWhitespaceAndMore("!🐱!↗", new char[]{'\uD83D'}));
    }
    
    @Test
    public void testCodePointSubstring() {
        assertEquals("test", StringUtil.codePointSubstring("𤭢 test", 2, 6));
        assertEquals(" tes", "𤭢 test".substring(2, 6));
        
        assertEquals("test", StringUtil.codePointSubstring("test 𤭢", 0, 4));
        assertEquals("test", "test 𤭢".substring(0, 4));
        
        assertEquals("𤭢", StringUtil.codePointSubstring("test 𤭢", 5, 6));
        assertEquals("\uD852", "test 𤭢".substring(5, 6));
        
        assertEquals("𤭢", StringUtil.codePointSubstring("test 𤭢 ", 5, 6));
        assertEquals("\uD852", "test 𤭢 ".substring(5, 6));
        
        assertEquals("𤭢𤭢", StringUtil.codePointSubstring("test 𤭢𤭢", 5, 7));
        assertEquals("𤭢", "test 𤭢𤭢 ".substring(5, 7));
    }
    
}
