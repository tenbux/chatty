package chatty.util.chatlog;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A line of a channel log that matters to the stream leaderboard: either a chat
 * message or a live/offline status marker.
 */
public sealed interface LeaderboardLogLine permits LeaderboardLogLine.ChatLine, LeaderboardLogLine.StatusMarker {

    /**
     * A chat message or /me action.
     *
     * @param timeMs epoch milliseconds of the log timestamp
     * @param key lowercase grouping key (the login for localized display names)
     * @param name display name without user status sigils
     * @param isBot whether the chatter is a known bot
     */
    record ChatLine(long timeMs, String key, String name, boolean isBot) implements LeaderboardLogLine {
    }

    /**
     * A marker the logger writes when the stream goes live or offline.
     */
    record StatusMarker(long timeMs, boolean isLive) implements LeaderboardLogLine {
    }

    /**
     * Parses one log line.
     *
     * @param line a raw line from a channel log file
     * @param zone zone the log timestamps were written in
     * @return the parsed line, or null for anything that is neither a chat
     * message nor a status marker (headers, info, system messages, separators,
     * malformed lines)
     */
    static LeaderboardLogLine parse(String line, ZoneId zone) {
                Matcher marker = Parsing.MARKER.matcher(line);
        if (marker.matches()) {
            Long timeMs = Parsing.toEpochMs(marker.group(1), zone);
            if (timeMs == null) {
                return null;
            }
            return new StatusMarker(timeMs, marker.group(2).equals("LIVE"));
        }
        Matcher chat = Parsing.CHAT.matcher(line);
        if (chat.matches()) {
            return Parsing.toChatLine(chat, zone);
        }
        return null;
    }

    /**
     * Holds the parsing helpers because interface members cannot be private
     * static state without being part of the public type.
     */
    final class Parsing {

        private static final String TIME = "(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})";
        private static final Pattern CHAT = Pattern.compile("^\\[" + TIME + "\\] <([^>]+)>\\*? (.*)$");
        private static final Pattern MARKER = Pattern.compile("^\\[" + TIME + "\\] \\*\\* .+ is now (LIVE|OFFLINE) \\*\\*$");
        // Localized display names are logged as "DisplayName (login)".
        private static final Pattern LOCALIZED_NAME = Pattern.compile(".* \\(([^()\\s]+)\\)$");
        private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        private static final String SIGILS = "~&@%$+!^";
        private static final char BOT_SIGIL = '^';
        private static final Set<String> KNOWN_BOTS = Set.of(
                "streamelements", "nightbot", "moobot", "streamlabs", "fossabot", "sery_bot");

        private Parsing() {
        }

        private static Long toEpochMs(String timestamp, ZoneId zone) {
            try {
                return LocalDateTime.parse(timestamp, TIME_FORMAT).atZone(zone).toInstant().toEpochMilli();
            } catch (DateTimeParseException ex) {
                // Matches the digit pattern but is not a real date, e.g. month 13.
                return null;
            }
        }

        private static ChatLine toChatLine(Matcher chat, ZoneId zone) {
            Long timeMs = toEpochMs(chat.group(1), zone);
            if (timeMs == null) {
                return null;
            }
            String raw = chat.group(2);
            int start = 0;
            boolean hasBotSigil = false;
            while (start < raw.length() && SIGILS.indexOf(raw.charAt(start)) >= 0) {
                hasBotSigil |= raw.charAt(start) == BOT_SIGIL;
                start++;
            }
            String name = raw.substring(start);
            if (name.isEmpty()) {
                return null;
            }
            Matcher localized = LOCALIZED_NAME.matcher(name);
            String key = (localized.matches() ? localized.group(1) : name).toLowerCase();
            return new ChatLine(timeMs, key, name, hasBotSigil || KNOWN_BOTS.contains(key));
        }
    }
}
