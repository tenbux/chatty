
package chatty.util.irc;

/**
 * Result of deriving the notification label and display text for a
 * USERNOTICE from its tags, independent of any live-connection side
 * effects. Shared between the live IRC path and outage backfill, so both
 * produce identical text for the same tags.
 *
 * @author tduva
 */
public record UsernoticeInfo(String type, String text, int months, boolean dropAttachedMessage) {

}
