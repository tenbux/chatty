
package chatty.util.api.eventsub;

import chatty.util.api.eventsub.payloads.*;
import org.json.simple.JSONObject;

/**
 * Data of a message. Different subclasses contain topic specific data.
 * 
 * @author tduva
 */
public class Payload {

    public final long created_at = System.currentTimeMillis();

    public static Payload decode(JSONObject payload, String msgType, String subType) {
        if (payload == null) {
            return null;
        }
        switch (msgType) {
            case "session_welcome":
            case "session_reconnect":
                return SessionPayload.decode(payload);
            case "revocation":
                return SubscriptionPayload.decode(payload);
        }
        
        if (subType != null) {
            switch (subType) {
                case "channel.raid":
                    return RaidPayload.decode(payload);
                case "channel.poll.begin":
                case "channel.poll.end":
                    return PollPayload.decode(payload);
                case "channel.shield_mode.begin":
                case "channel.shield_mode.end":
                    return ShieldModePayload.decode(payload);
                case "channel.shoutout.create":
                    return ShoutoutPayload.decode(payload);
                case "channel.moderate":
                    return ModActionPayload.decode(payload);
                case "automod.message.hold":
                    return ModActionPayload.decodeAutomodHeld(payload);
                case "automod.message.update":
                    return ModActionPayload.decodeAutomodUpdate(payload);
                case "channel.suspicious_user.message":
                    return SuspiciousMessagePayload.decode(payload);
                case "channel.suspicious_user.update":
                    return SuspiciousUpdatePayload.decode(payload);
                case "channel.warning.acknowledge":
                    return WarningAcknowledgePayload.decode(payload);
                case "channel.chat.user_message_hold", "channel.chat.user_message_update":
                    return UserMessageHeldPayload.decode(payload);
                case "channel.channel_points_custom_reward_redemption.add":
                    return ChannelPointsRedemptionPayload.decode(payload, false);
                case "channel.channel_points_custom_reward_redemption.update":
                    return ChannelPointsRedemptionPayload.decode(payload, true);
            }
        }
        
        return null;
    }

}
