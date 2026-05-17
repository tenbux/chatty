
package chatty.util.api.eventsub;

import chatty.util.Debugging;
import chatty.util.JSONUtil;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Received message.
 *
 * @param data Data of the message. Can be null.
 * @author tduva
 */
public record Message(String type, String id, String subType, String subVersion, long timestamp, Payload data) {

    private static final Logger LOGGER = Logger.getLogger(Message.class.getName());

    public static Message fromJson(String json) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject root = (JSONObject) parser.parse(json);

            JSONObject metadata = (JSONObject) root.get("metadata");
            JSONObject payload = (JSONObject) root.get("payload");

            String type = JSONUtil.getString(metadata, "message_type");
            String id = JSONUtil.getString(metadata, "message_id");
            long timestamp = JSONUtil.getDatetime(metadata, "message_timestamp", 0);
            String subType = JSONUtil.getString(metadata, "subscription_type");
            String subVersion = JSONUtil.getString(metadata, "subscription_version");

            Payload data = Payload.decode(payload, type, subType);
            return new Message(type, id, subType, subVersion, timestamp, data);
        } catch (Exception ex) {
            LOGGER.warning(String.format("[EventSub] Error parsing message: %s %s",
                    Debugging.getStacktraceFilteredFlat(ex), json));
            return null;
        }
    }

    @Override
    public String toString() {
        return type + "[" + id + "/" + "/" + data + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Message other = (Message) obj;
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        return Objects.equals(this.data, other.data);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + Objects.hashCode(this.type);
        hash = 61 * hash + Objects.hashCode(this.id);
        hash = 61 * hash + Objects.hashCode(this.data);
        return hash;
    }

}
