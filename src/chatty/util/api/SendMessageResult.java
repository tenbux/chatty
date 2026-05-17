
package chatty.util.api;

import chatty.util.JSONUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.logging.Logger;

/**
 *
 * @author tduva
 */
public class SendMessageResult {

    private static final Logger LOGGER = Logger.getLogger(SendMessageResult.class.getName());
    
    public final boolean wasSent;
    public final String msgId;
    public final String dropReasonMessage;
    
    protected SendMessageResult(boolean ok, String msgId, String dropReasonMessage) {
        this.wasSent = ok;
        this.msgId = msgId;
        this.dropReasonMessage = dropReasonMessage;
    }
    
    public static SendMessageResult parse(String json) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject root = (JSONObject) parser.parse(json);
            JSONArray data = (JSONArray) root.get("data");
            JSONObject actualData = (JSONObject) data.getFirst();
            String msgId = JSONUtil.getString(actualData, "message_id");
            boolean isOk = JSONUtil.getBoolean(actualData, "is_sent", false);
            JSONObject dropReason = (JSONObject) actualData.get("drop_reason");
            String dropCode = null;
            String dropMessage = null;
            if (dropReason != null) {
                JSONUtil.getString(dropReason, "code");
                dropMessage = JSONUtil.getString(dropReason, "message");
            }
            return new SendMessageResult(isOk, msgId, dropMessage);
        }
        catch (Exception ex) {
            LOGGER.warning("Failed sending message: "+ex);
        }
        return new SendMessageResult(false, null, "An error occured sending the message");
    }
    
}
