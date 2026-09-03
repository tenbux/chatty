
package chatty.util.history;

import chatty.util.UrlRequest;
import chatty.util.irc.MsgTags;
import chatty.util.irc.ParsedMsg;
import chatty.util.settings.Settings;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Fetches messages missed during a connection outage from a public
 * rustlog-family log mirror (configurable, not Twitch-official), as a
 * best-effort second source alongside {@link HistoryManager}'s
 * recent-messages.robotty.de. Unlike the regular history-service catch-up
 * on join, this is scoped to a specific gap (a real detected outage) and
 * the recovered messages preserve their original timestamp for logging.
 *
 * @author tduva
 */
public class OutageBackfillManager {

    private static final Logger LOGGER = Logger.getLogger(OutageBackfillManager.class.getName());

    private final Settings settings;

    public OutageBackfillManager(Settings settings) {
        this.settings = settings;
    }

    public boolean isEnabled() {
        return settings.getBoolean("outageBackfillEnabled");
    }

    public long getMinDowntimeMs() {
        return settings.getLong("outageBackfillMinDowntime") * 1000;
    }

    /**
     * Fetches messages sent to {@code stream} within [gapStartMs, gapEndMs]
     * from the configured mirror, parsed into {@link OutageMessage}
     * objects ready for rendering. Always calls {@code listener} exactly
     * once, with an empty list on any failure (mirror down, bad response,
     * unreachable) -- this is best-effort, third-party coverage, not a
     * source that should ever block or fail the caller.
     */
    public void getMirrorMessages(String stream, long gapStartMs, long gapEndMs,
            Consumer<List<OutageMessage>> listener) {
        String mirrorUrl = settings.getString("outageBackfillMirrorUrl");
        if (mirrorUrl == null || mirrorUrl.isBlank()) {
            listener.accept(new ArrayList<>());
            return;
        }
        try {
            // UrlRequest's own error handling doesn't cover a malformed
            // URL (URI.create throws IllegalArgumentException, not an
            // IOException, from inside its background thread, silently
            // killing it without ever calling back) -- validate the base
            // URL here, synchronously, before any per-day request can hang.
            URI.create(stripTrailingSlash(mirrorUrl));
        } catch (IllegalArgumentException ex) {
            LOGGER.warning("Invalid outage backfill mirror URL, skipping: " + mirrorUrl);
            listener.accept(new ArrayList<>());
            return;
        }
        List<long[]> days = utcDaysInRange(gapStartMs, gapEndMs);
        if (days.isEmpty()) {
            listener.accept(new ArrayList<>());
            return;
        }

        List<OutageMessage> combined = new ArrayList<>();
        AtomicInteger pending = new AtomicInteger(days.size());
        for (long[] day : days) {
            String url = String.format("%s/channel/%s/%d/%d/%d?json",
                    stripTrailingSlash(mirrorUrl), stream, day[0], day[1], day[2]);
            UrlRequest request = new UrlRequest(url);
            request.setLabel("OutageBackfill/");
            request.setTimeouts(8000, 8000);
            request.async((String resultText, int responseCode) -> {
                if (responseCode == 200) {
                    try {
                        parseMirrorDayResponse(resultText, gapStartMs, gapEndMs, combined);
                    } catch (Exception ex) {
                        LOGGER.warning("Error parsing outage backfill mirror response: " + ex);
                    }
                } else {
                    LOGGER.warning("Outage backfill mirror request failed (" + responseCode + "): " + url);
                }
                if (pending.decrementAndGet() == 0) {
                    listener.accept(combined);
                }
            });
        }
    }

    private static void parseMirrorDayResponse(String resultText, long gapStartMs, long gapEndMs,
            List<OutageMessage> out) throws ParseException {
        JSONParser parser = new JSONParser();
        JSONObject root = (JSONObject) parser.parse(resultText);
        JSONArray messages = (JSONArray) root.get("messages");
        if (messages == null) {
            return;
        }
        for (Object o : messages) {
            String raw = (String) ((JSONObject) o).get("raw");
            OutageMessage msg = parseRawLine(raw, gapStartMs, gapEndMs);
            if (msg != null) {
                synchronized (out) {
                    out.add(msg);
                }
            }
        }
    }

    /**
     * Parses a single raw IRC line (tags + command + params, as sent by
     * Twitch and preserved verbatim by the mirror) into an
     * {@link OutageMessage}, if it's a PRIVMSG or USERNOTICE within the
     * gap window. Uses Chatty's own {@link ParsedMsg} IRC parser, the same
     * one the live connection and the regular history service use.
     */
    static OutageMessage parseRawLine(String raw, long gapStartMs, long gapEndMs) {
        if (raw == null) {
            return null;
        }
        ParsedMsg parsed = ParsedMsg.parse(raw);
        if (parsed == null) {
            return null;
        }
        MsgTags tags = parsed.getTags();
        long timestampMs = tags.getLong("tmi-sent-ts", -1);
        if (timestampMs < gapStartMs || timestampMs > gapEndMs) {
            return null;
        }
        String command = parsed.getCommand();
        if (!command.equals("PRIVMSG") && !command.equals("USERNOTICE")) {
            return null;
        }
        if (!parsed.getParameters().has(0) || !parsed.getParameters().isChan(0)) {
            return null;
        }
        String nick = parsed.getNick();
        if (nick == null || nick.isEmpty()) {
            return null;
        }
        String text = parsed.getParameters().has(1) ? parsed.getParameters().get(1) : "";
        boolean action = false;
        if (command.equals("PRIVMSG") && text.length() > 1 && text.charAt(0) == (char) 1
                && text.startsWith("ACTION", 1)) {
            action = true;
            text = text.substring(7).trim();
        }
        return new OutageMessage(command, nick, text, action, tags, timestampMs);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * @return {year, month, day} (1-indexed month/day) for each UTC
     * calendar date the [startMs, endMs] range touches.
     */
    static List<long[]> utcDaysInRange(long startMs, long endMs) {
        List<long[]> days = new ArrayList<>();
        if (endMs < startMs) {
            return days;
        }
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(startMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        while (cal.getTimeInMillis() <= endMs) {
            days.add(new long[]{
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            });
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return days;
    }

}
