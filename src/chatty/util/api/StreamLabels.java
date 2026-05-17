
package chatty.util.api;

import chatty.Chatty;
import chatty.util.JSONUtil;
import chatty.util.StringUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.*;
import java.util.logging.Logger;

/**
 *
 * @author tduva
 */
public class StreamLabels {
    
    private static final Logger LOGGER = Logger.getLogger(StreamLabels.class.getName());
    
    private static final List<String> EDITABLE = new ArrayList<>(List.of(
        "DrugsIntoxication",
        "SexualThemes",
        "ViolentGraphic",
        "Gambling",
        "ProfanityVulgarity"
    ));

    private static final List<String> AUTO = new ArrayList<>(List.of(
        "MatureGame"
    ));
    
    public static List<StreamLabel> copyEditableLabelsOnly(List<StreamLabel> labels) {
        List<StreamLabel> editableLabels = new ArrayList<>(labels);
        editableLabels.removeIf(label -> !label.isEditable());
        return editableLabels;
    }
    
    public static List<StreamLabel> copyAutoLabelsOnly(List<StreamLabel> labels) {
        List<StreamLabel> editableLabels = new ArrayList<>(labels);
        editableLabels.removeIf(StreamLabel::isEditable);
        return editableLabels;
    }
    
    private static boolean isEditableLabel(String labelId) {
        return EDITABLE.contains(labelId);
    }
    
    public static List<StreamLabel> getAvailableLabels() {
        List<StreamLabel> labels = new ArrayList<>();
        EDITABLE.forEach(id -> labels.add(new StreamLabel(id)));
        AUTO.forEach(id -> labels.add(new StreamLabel(id)));
        return labels;
    }

    public record StreamLabel(String id) implements Comparable<StreamLabel> {

        public static final StreamLabel EMPTY = new StreamLabel("");

        public String getDisplayName() {
                StreamLabelInfo info = getInfo(id);
                return info != null ? info.name : id;
            }

        public String getDescription() {
                StreamLabelInfo info = getInfo(id);
                return info != null ? info.description : null;
            }

        public boolean isValid() {
                return !StringUtil.isNullOrEmpty(id) && !id.contains(" ");
            }

        public boolean isEditable() {
                return isEditableLabel(id);
            }

        @Override
            public String toString() {
                return id();
            }

        @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final StreamLabel other = (StreamLabel) obj;
                return Objects.equals(this.id, other.id);
            }

        @Override
            public int compareTo(StreamLabel o) {
                return id.compareToIgnoreCase(o.id);
            }

        }
    
    private static final String CACHE_FILE = Chatty.getPathCreate(Chatty.PathType.CACHE).resolve("streamlabels").toString();
    public static final int CACHE_EXPIRES_AFTER = 60*60*24*7;
    
    private static final CachedManager cache = new CachedManager(CACHE_FILE, CACHE_EXPIRES_AFTER, "StreamLabels") {
        
        @Override
        public boolean handleData(String data) {
            if (data != null) {
                return parseInfo(data);
            }
            return false;
        }
    };
    
    public static void request(Requests requests) {
        if (cache.load()) {
            requests.getContentLabels();
        }
    }
    
    public static void dataReceived(String json) {
        cache.dataReceived(json, false);
    }
    
    private static boolean parseInfo(String json) {
        try {
            Map<String, StreamLabelInfo> result = new HashMap<>();
            JSONObject root = (JSONObject) new JSONParser().parse(json);
            JSONArray data = (JSONArray) root.get("data");
            for (Object o : data) {
                JSONObject entry = (JSONObject) o;
                StreamLabelInfo info = new StreamLabelInfo(
                        JSONUtil.getString(entry, "id"),
                        JSONUtil.getString(entry, "name"),
                        JSONUtil.getString(entry, "description"));
                result.put(info.id, info);
            }
            infos = result;
            return true;
        }
        catch (Exception ex) {
            LOGGER.warning("Error parsing streamlabels: "+ex);
        }
        return false;
    }
    
    private static Map<String, StreamLabelInfo> infos;
    
    private static StreamLabelInfo getInfo(String id) {
        if (infos != null) {
            return infos.get(id);
        }
        return null;
    }

    public record StreamLabelInfo(String id, String name, String description) {

    }
    
}
