package chatty.util.chatlog;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import static org.junit.Assert.*;
import org.junit.Test;

public class LeaderboardLogLineTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static long ms(String timestamp) {
        return LocalDateTime.parse(timestamp, FORMAT).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static LeaderboardLogLine.ChatLine parseChat(String line) {
        LeaderboardLogLine parsed = LeaderboardLogLine.parse(line, ZONE);
        assertTrue("expected ChatLine but got " + parsed, parsed instanceof LeaderboardLogLine.ChatLine);
        return (LeaderboardLogLine.ChatLine) parsed;
    }

    @Test
    public void test_parse_plainMessage_returnsChatLine() {
        LeaderboardLogLine parsed = LeaderboardLogLine.parse("[2026-10-31 20:00:05] <@Viewer_One> hi", ZONE);
        assertEquals(new LeaderboardLogLine.ChatLine(ms("2026-10-31 20:00:05"), "viewer_one", "Viewer_One", false), parsed);
    }

    @Test
    public void test_parse_meAction_returnsChatLine() {
        LeaderboardLogLine.ChatLine chat = parseChat("[2026-10-31 20:00:05] <Viewer_One>* waves");
        assertEquals("viewer_one", chat.key());
        assertEquals("Viewer_One", chat.name());
        assertFalse(chat.isBot());
    }

    @Test
    public void test_parse_botSigil_isBot() {
        LeaderboardLogLine.ChatLine chat = parseChat("[2026-10-31 20:00:05] <%^SomeBot> x");
        assertTrue(chat.isBot());
        assertEquals("somebot", chat.key());
        assertEquals("SomeBot", chat.name());
    }

    @Test
    public void test_parse_knownBotName_isBot() {
        assertTrue(parseChat("[2026-10-31 20:00:05] <StreamElements> x").isBot());
    }

    @Test
    public void test_parse_localizedName_groupsByLogin() {
        LeaderboardLogLine.ChatLine chat = parseChat("[2026-10-31 20:00:05] <+名前 (viewer_two)> x");
        assertEquals("viewer_two", chat.key());
        assertEquals("名前 (viewer_two)", chat.name());
    }

    @Test
    public void test_parse_liveMarker_returnsStatusMarker() {
        assertEquals(new LeaderboardLogLine.StatusMarker(ms("2026-10-31 19:58:00"), true),
                LeaderboardLogLine.parse("[2026-10-31 19:58:00] ** Some Streamer is now LIVE **", ZONE));
    }

    @Test
    public void test_parse_offlineMarker_returnsStatusMarker() {
        assertEquals(new LeaderboardLogLine.StatusMarker(ms("2026-10-31 23:10:00"), false),
                LeaderboardLogLine.parse("[2026-10-31 23:10:00] ** Some Streamer is now OFFLINE **", ZONE));
    }

    @Test
    public void test_parse_nonChat_returnsNull() {
        assertNull(LeaderboardLogLine.parse("# Log started: 2026-10-31 19:58:00", ZONE));
        assertNull(LeaderboardLogLine.parse("-", ZONE));
        assertNull(LeaderboardLogLine.parse("[2026-10-31 19:58:00] You have joined #testchan", ZONE));
        assertNull(LeaderboardLogLine.parse("[2026-10-31 19:58:00] ~Title (Game)~", ZONE));
        assertNull(LeaderboardLogLine.parse("", ZONE));
    }

    @Test
    public void test_parse_timestampInOtherZone_convertsUsingGivenZone() {
        LeaderboardLogLine.ChatLine chat = parseChat("[2026-10-31 20:00:05] <viewer_one> hi");
        LeaderboardLogLine other = LeaderboardLogLine.parse("[2026-10-31 20:00:05] <viewer_one> hi", ZoneId.of("America/New_York"));
        assertEquals(4 * 3600_000L, ((LeaderboardLogLine.ChatLine) other).timeMs() - chat.timeMs());
    }

    @Test
    public void test_parse_messageWithLineSeparator_returnsChatLine() {
        LeaderboardLogLine.ChatLine chat = parseChat("[2026-10-31 20:00:05] <viewer_one> first\u2028second");
        assertEquals("viewer_one", chat.key());
    }

    @Test
    public void test_parse_turkishDefaultLocale_keyStaysAscii() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            LeaderboardLogLine.ChatLine chat = parseChat("[2026-10-31 20:00:05] <VIEWER_ONE> hi");
            assertEquals("viewer_one", chat.key());
        } finally {
            Locale.setDefault(original);
        }
    }
}
