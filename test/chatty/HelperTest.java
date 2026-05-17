
package chatty;

import chatty.util.StringUtil;
import org.junit.Test;

import java.util.*;
import java.util.regex.Matcher;

import static org.junit.Assert.*;

/**
 *
 * @author tduva
 */
public class HelperTest {
    
    @Test
    public void validateChannelTest() {
        assertTrue(Helper.isValidChannel("joshimuz"));
        assertTrue(Helper.isValidChannel("51nn3r"));
        assertTrue(Helper.isValidChannel("#joshimuz"));
        assertFalse(Helper.isValidChannel("##joshimuz"));
        assertFalse(Helper.isValidChannel(""));
        assertFalse(Helper.isValidChannel(" "));
        assertFalse(Helper.isValidChannel("abc$"));
        
        assertTrue(Helper.isValidChannelStrict("#joshimuz"));
        assertFalse(Helper.isValidChannelStrict("joshimuz"));
        
        assertTrue(Helper.isRegularChannel("#joshimuz"));
        assertTrue(Helper.isRegularChannel("joshimuz"));
        assertFalse(Helper.isRegularChannel(""));
        assertFalse(Helper.isRegularChannel("#chatrooms:1234:abc-def"));
        assertFalse(Helper.isRegularChannel("chatrooms:"));
        
        assertTrue(Helper.isRegularChannelStrict("#joshimuz"));
        assertTrue(Helper.isRegularChannelStrict("#underscore_"));
        assertFalse(Helper.isRegularChannelStrict("joshimuz"));
        assertFalse(Helper.isRegularChannelStrict("#chatrooms:1234:abc-def"));
        
        assertTrue(Helper.isValidStream("joshimuz"));
        assertTrue(Helper.isValidStream("51nn3r"));
        assertFalse(Helper.isValidStream("chatrooms:1234:abc-def"));
        assertFalse(Helper.isValidStream(null));
        assertFalse(Helper.isValidStream(""));
        assertFalse(Helper.isValidStream(" "));
        
        assertEquals("channel", Helper.toStream("#channel"));
        assertEquals("", Helper.toStream(""));
        assertEquals("chatrooms:1234:abc-def", Helper.toStream("#chatrooms:1234:abc-def"));
        
        assertEquals("channel", Helper.toValidStream("#channel"));
        assertNull(Helper.toValidStream(""));
        assertNull(Helper.toValidStream("#chatrooms:1234:abc-def"));
        
        assertEquals("#channel", Helper.toValidChannel("#channel"));
        assertEquals("#channel", Helper.toValidChannel("channel"));
        assertNull(Helper.toValidChannel("$channel"));
        assertEquals("#chatrooms:1234:abc-def", Helper.toValidChannel("chatrooms:1234:abc-def"));
        assertEquals("#abc", Helper.toValidChannel("abc"));
        assertNull(Helper.toValidChannel(""));
        assertNull(Helper.toValidChannel("#"));
        assertNull(Helper.toValidChannel(" 1"));
        assertEquals("#abc", Helper.toValidChannel("#abc"));
    }
    
    @Test
    public void removeDuplicateWhitespaceTest() {
        assertEquals(" ", StringUtil.removeDuplicateWhitespace(" "));
        assertEquals("", StringUtil.removeDuplicateWhitespace(""));
        assertEquals("abc", StringUtil.removeDuplicateWhitespace("abc"));
        assertEquals("a b", StringUtil.removeDuplicateWhitespace("a  b"));
        assertEquals(" ", StringUtil.removeDuplicateWhitespace("       "));
        assertEquals(" a b ", StringUtil.removeDuplicateWhitespace(" a  b  "));
    }
    
    @Test
    public void htmlspecialchars_encodeTest() {
        assertEquals("&amp;", Helper.htmlspecialchars_encode("&"));
        assertEquals("&amp;amp;", Helper.htmlspecialchars_encode("&amp;"));
        assertEquals("hello john &amp; everyone else", Helper.htmlspecialchars_encode("hello john & everyone else"));
        assertEquals("&lt;", Helper.htmlspecialchars_encode("<"));
        assertEquals("&gt;", Helper.htmlspecialchars_encode(">"));
        assertEquals("&quot;", Helper.htmlspecialchars_encode("\""));
        assertEquals("&amp; &gt;", Helper.htmlspecialchars_encode("& >"));
    }
    
    @Test
    public void htmlspecialchars_decodeTest() {
        assertEquals("&", Helper.htmlspecialchars_decode("&amp;"));
        assertEquals("\"", Helper.htmlspecialchars_decode("&quot;"));
        assertEquals("<", Helper.htmlspecialchars_decode("&lt;"));
        assertEquals(">", Helper.htmlspecialchars_decode("&gt;"));
        assertEquals("abc & test", Helper.htmlspecialchars_decode("abc &amp; test"));
    }
    
    @Test
    public void tagsvalue_decodeTest() {
        assertEquals(" ", Helper.tagsvalue_decode("\\s"));
        assertEquals(";", Helper.tagsvalue_decode("\\:"));
        assertEquals("\n", Helper.tagsvalue_decode("\\n"));
        assertEquals("\\s", Helper.tagsvalue_decode("\\\\s"));
        assertEquals("abc test", Helper.tagsvalue_decode("abc\\stest"));
        assertEquals("", Helper.tagsvalue_decode(""));
        assertNull(Helper.tagsvalue_decode(null));
        assertEquals(" ", Helper.tagsvalue_decode(" "));
        assertEquals("\\s ;", Helper.tagsvalue_decode("\\\\s\\s\\:"));
    }
    
    @Test
    public void testUrls() {
        
        //---------------
        // Just matching
        //---------------
        String[] shouldMatch = new String[]{
            "twitch.tv",
            "Twitch.TV",
            "twitch.tv/abc",
            "twitch.tv/ABC",
            "http://twitch.tv",
            "https://twitch.tv",
            "amazon.de/ääh",
            "https://google.de",
            "google.com/abc(blah)",
            "http://börse.de",
            "www.twitch.tv/",
            "twitch.tv🐁"
        };
        
        /**
         * Trying to match the full string, so it's not necessary to check the
         * matches. Ofc it would find an URL in most of those.
         */
        String[] shouldNotMatch = new String[]{
            "twitch.tv.",
            "twitch.tv ",
            " twitch.tv",
            " twitch.tv ",
            "https:/twitch.tv",
            "https://twitch.tv:",
            "twitch.tv:",
            "twitch.tv,",
            "www.twitch.tv,",
            "www.twitch.tv/,",
            "asdas.abc",
            "http://",
            "http://www.",
            "https://",
            "https://www."
        };
        
        for (String test : shouldMatch) {
            assertTrue("Should match: "+test, Helper.getUrlPattern().matcher(test).matches());
        }
        
        for (String test : shouldNotMatch) {
            assertFalse("Should not match: "+test, Helper.getUrlPattern().matcher(test).matches());
        }
        
        //-------------------
        // Finding in String
        //-------------------
        String find = "Abc http://example.com http://example.com/abc(test) dumdidum(http://example.com) [http://example.com] "
                + "(http://example.com)[github.io] [github.io] (github.io) 🐣twitch.tv🐣 https:// www. not.a/domain "
                + "twitch.tv/🐁 twitch.tv github.io $ chatty.github.io Other text and whatnot https://chatty.github.io";
        String[] findTarget = new String[]{
            "http://example.com",
            "http://example.com/abc(test)",
            "http://example.com)",
            "http://example.com]",
            "http://example.com)[github.io]",
            "github.io]",
            "github.io)",
            "twitch.tv🐣",
            "twitch.tv/🐁",
            "twitch.tv",
            "github.io",
            "chatty.github.io",
            "https://chatty.github.io"
        };

        Matcher m = Helper.getUrlPattern().matcher(find);
        int i = 0;
        while (m.find()) {
            String found = m.group();
            String target = findTarget[i];
            assertEquals("Should have found: " + target + " found: " + found, found, target);
            i++;
        }
    }
    
    @Test
    public void removeEmojiVariationSelectorTest() {
        assertNull(Helper.removeEmojiVariationSelector(null));
        assertEquals("❤", Helper.removeEmojiVariationSelector("❤︎"));
        assertEquals("❤", Helper.removeEmojiVariationSelector("❤︎︎"));
        assertEquals("❤", Helper.removeEmojiVariationSelector("️❤︎︎"));
        assertEquals("", Helper.removeEmojiVariationSelector("️︎"));
        assertEquals("❤ 	❤ 	❤", Helper.removeEmojiVariationSelector("❤️ 	❤ 	❤︎"));
    }
    
    @Test
    public void getChainedCommandsTest() {
        chainedTest("", new String[]{});
        chainedTest(null, new String[]{});
        chainedTest("a", new String[]{"a"});
        chainedTest("a | b", new String[]{"a", "b"});
        chainedTest("a|b", new String[]{"a", "b"});
        chainedTest("a || b", new String[]{"a | b"});
        chainedTest("a||b", new String[]{"a|b"});
        chainedTest("a|||b", new String[]{"a||b"});
        chainedTest("|b", new String[]{"b"});
        chainedTest("a|b           |c", new String[]{"a", "b", "c"});
        chainedTest("||a|| | b | c", new String[]{"|a|", "b", "c"});
        chainedTest("||||||", new String[]{"|||||"});
        chainedTest("|||||| |", new String[]{"|||||"});
        chainedTest("|||||| | ||", new String[]{"|||||", "|"});
        chainedTest("| | | | | |", new String[]{});
        chainedTest("a | /chain b", new String[]{"a", "/chain b"});
        chainedTest("a |c \\|d |1||", new String[]{"a", "c \\", "d", "1|"});
        chainedTest("b | a        ", new String[]{"b", "a"});
    }
    
    private static void chainedTest(String input, String[] result) {
        assertArrayEquals(Helper.getChainedCommands(input).toArray(), result);
    }
    
    @Test
    public void getForeachParamsTest() {
        foreachTest(null, null, null);
        foreachTest("", null, null);
        foreachTest("a b c", "a b c", null);
        foreachTest(">", null, null);
        foreachTest("a b c>abc", "a b c", "abc");
        foreachTest("a b c > abc", "a b c", "abc");
        foreachTest("a b c  >  abc", "a b c", "abc");
        foreachTest(">abc", null, "abc");
        foreachTest("     >abc", null, "abc");
        foreachTest(">> >abc", ">", "abc");
        foreachTest(">> >abc<<", ">", "abc<<");
        foreachTest(">> >abc>>", ">", "abc>");
        foreachTest(">> >abc>", ">", "abc>");
        foreachTest("weaf >> fawef", "weaf > fawef", null);
        foreachTest("abc >     ", "abc", null);
    }
    
    private static void foreachTest(String input, String... result) {
        assertArrayEquals(Helper.getForeachParams(input), result);
    }
    
    @Test
    public void parseChannelsFromStringTest() {
        parseChannelsTest("", true);
        parseChannelsTest("$abc", true);
        parseChannelsTest("#jaf-jiaefw,", true);
        parseChannelsTest("#abc", true, "#abc");
        parseChannelsTest("abc", true, "#abc");
        parseChannelsTest("abc", false, "abc");
        parseChannelsTest("#abc, abc", true, "#abc");
        parseChannelsTest("#abc, abc", false, "#abc", "abc");
        parseChannelsTest("#abc,abc", false, "#abc", "abc");
        parseChannelsTest(" #abc,     abc ", false, "#abc", "abc");
        parseChannelsTest(" #abc,     \nabc ", false, "#abc", "abc");
        parseChannelsTest(" #abc, $##afwe, abc, # ", false, "#abc", "abc");
        
        Addressbook ab = new Addressbook(null, null, null);
        ab.add("#chan", "cat");
        ab.add("user", "cat");
        ab.add("#chan2", "cat2");
        ab.add("user2", "cat2");
        
        parseChannelsTest("[cat]", true);
        Helper.parseChannelHelper = new Helper.ParseChannelHelper() {
            @Override
            public Collection<String> getFavorites() {
                return Arrays.asList("#favChan1", "#favChan2", "#favChan3");
            }

            @Override
            public Collection<String> getNamesByCategory(String category) {
                return ab.getNamesByCategory(category);
            }

            @Override
            public boolean isStreamLive(String stream) {
                return !stream.equals("chan2") && !stream.equals("favChan3");
            }
        };
        parseChannelsTest("[cat]", false, "#chan", "user");
        parseChannelsTest("[cat]", true, "#chan", "#user");
        parseChannelsTest("[cat ]", true, "#chan", "#user");
        parseChannelsTest("[cat #]", true, "#chan");
        parseChannelsTest("[cat !#]", true, "#user");
        parseChannelsTest("[cat2]", true, "#chan2", "#user2");
        parseChannelsTest("#chan, [cat2]", true, "#chan", "#chan2", "#user2");
        parseChannelsTest("#chan, [cat2], #chan3", true, "#chan", "#chan2", "#user2", "#chan3");
        
        parseChannelsTest("[*]", true, "#favchan1", "#favchan2", "#favchan3");
        parseChannelsTest("[* live]", true, "#favchan1", "#favchan2");
        
        parseChannelsTest("[cat live]", true, "#chan", "#user");
        parseChannelsTest("[cat2 # live]", true);
        parseChannelsTest("[cat2 live]", true, "#user2");
        parseChannelsTest("[cat live], [cat2 live]", true, "#chan", "#user", "#user2");
        
        Helper.parseChannelHelper = null;
        parseChannelsTest("#chan, [cat2], #chan3", true, "#chan", "#chan3");
        parseChannelsTest("[*]", true);
        parseChannelsTest("[cat live]", true);
    }
    
    private static void parseChannelsTest(String input, boolean prepend, String... result) {
        assertEquals(new HashSet<>(Arrays.asList(result)), Helper.parseChannelsFromString(input, prepend));
    }
    
    @Test
    public void testGetChannelFromUrl() {
        assertEquals("channel", Helper.getChannelFromUrl("twitch.tv/channel"));
        assertEquals("channel", Helper.getChannelFromUrl("twitch.tv/channel/"));
        assertEquals("channel", Helper.getChannelFromUrl("twitch.tv/channel/about/"));
        assertEquals("channel_name", Helper.getChannelFromUrl("twitch.tv/channel_name/"));
        assertEquals("channel_123_abc_", Helper.getChannelFromUrl("twitch.tv/channel_123_abc_"));
        assertEquals("twitc.tv/channel/about/", Helper.getChannelFromUrl("twitc.tv/channel/about/"));
        assertEquals("channel", Helper.getChannelFromUrl("channel"));
        assertEquals("channel/", Helper.getChannelFromUrl("channel/"));
        assertEquals("twitch.tv/$%&", Helper.getChannelFromUrl("twitch.tv/$%&"));
        assertEquals("channel", Helper.getChannelFromUrl("https://twitch.tv/channel"));
        assertEquals("channel", Helper.getChannelFromUrl("http://twitch.tv/channel"));
        assertEquals("channel", Helper.getChannelFromUrl("https://www.twitch.tv/channel"));
        assertEquals("channel", Helper.getChannelFromUrl("http://www.twitch.tv/channel"));
        assertEquals("channel", Helper.getChannelFromUrl("www.twitch.tv/channel"));
        assertEquals("https:///twitch.tv/channel", Helper.getChannelFromUrl("https:///twitch.tv/channel"));
        assertEquals("https://wwww.twitch.tv/channel", Helper.getChannelFromUrl("https://wwww.twitch.tv/channel"));
        
        assertEquals("channel", Helper.getChannelFromUrl("https://www.twitch.tv/popout/channel/chat"));
        assertEquals("channel", Helper.getChannelFromUrl("https://www.twitch.tv/popout/channel/viewercard/username"));
        assertEquals("Channel123", Helper.getChannelFromUrl("https://www.twitch.tv/popout/Channel123/viewercard/username"));
        assertEquals("channel123_", Helper.getChannelFromUrl("https://www.twitch.tv/popout/channel123_/viewercard/username"));
        assertEquals("channel_123", Helper.getChannelFromUrl("https://www.twitch.tv/popout/channel_123/viewercard/username"));
    }
    
    @Test
    public void testGetPopoutUrlInfo() {
        testPopoutUrlInfo("https://www.twitch.tv/popout/channel/chat", "channel", "chat", null);
        testPopoutUrlInfo("https://www.twitch.tv/popout/channel/viewercard/username", "channel", "viewercard", "username");
        testPopoutUrlInfo("https://www.twitch.tv/popout/channel/viewercard/username/", "channel", "viewercard", "username");
        testPopoutUrlInfo("https://www.twitch.tv/popout/channel/viewercard/username/abc", "channel", "viewercard", "username");
        testPopoutUrlInfo("www.twitch.tv/popout/channel/viewercard/username/", "channel", "viewercard", "username");
        testPopoutUrlInfo("twitch.tv/popout/channel/viewercard/username/", "channel", "viewercard", "username");
        testPopoutUrlInfo("twitch.tv/popout/channel123/viewercard/user_name/", "channel123", "viewercard", "user_name");
    }
    
    private static void testPopoutUrlInfo(String url, String channel, String type, String username) {
        Helper.TwitchPopoutUrlInfo info = Helper.getPopoutUrlInfo(url);
        assertEquals(channel, info.channel);
        assertEquals(type, info.type);
        assertEquals(username, info.username);
    }
    
    @Test
    public void testChannelsListParsingRoundTrip() {
        Set<String> expected = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        expected.add("foo");
        expected.add("bar");
        expected.add("hello");
        expected.add("world");
        TreeSet<String> actual = channelListRoundTrip(expected, false);
        assertEquals(expected, actual);
    }

    @Test
    public void testPrependedChannelsListParsingRoundTrip() {
        Set<String> expected = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        expected.add("#foo");
        expected.add("#bar");
        expected.add("#hello");
        expected.add("#world");
        TreeSet<String> actual = channelListRoundTrip(expected, true);
        assertEquals(expected, actual);
    }

    private TreeSet<String> channelListRoundTrip(Set<String> original, boolean prepend) {
        TreeSet<String> copy = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        copy.addAll(Helper.parseChannelsFromString(Helper.buildStreamsString(original), prepend));
        return copy;
    }
}
