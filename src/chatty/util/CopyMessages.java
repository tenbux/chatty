
package chatty.util;

import chatty.User;
import chatty.util.settings.Settings;

import java.util.regex.Matcher;

/**
 *
 * @author tduva
 */
public class CopyMessages {
    
    public static void copyMessage(Settings settings, User user, String message,
            boolean highlighted) {
        if (!settings.getBoolean("cmEnabled")) {
            return;
        }
        if (settings.getBoolean("cmHighlightedOnly") && !highlighted) {
            return;
        }
        String channel = settings.getString("cmChannel");
        if (!channel.trim().isEmpty() && !channel.equalsIgnoreCase(user.getChannel())) {
            return;
        }
        String text = settings.getString("cmTemplate");
        // Quote the replacements: they're chat-derived text that can
        // contain "$" or "\", which replaceFirst() treats specially in the
        // replacement string (group references/escapes), throwing instead
        // of copying the message.
        text = text.replaceFirst("\\{user\\}", Matcher.quoteReplacement(user.getDisplayNick()));
        text = text.replaceFirst("\\{message\\}", Matcher.quoteReplacement(message));
        MiscUtil.copyToClipboard(text);
    }
    
}
