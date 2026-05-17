
package chatty.util.api;

import chatty.util.DateTime;
import chatty.util.JSONUtil;
import chatty.util.StringUtil;
import org.json.simple.JSONObject;

/**
 *
 * @author tduva
 */
public record UserInfo(String id, String login, String displayName, String broadcasterType, String description,
                       String profileImageUrl, long createdAt, int views) {

    public static UserInfo create(JSONObject data) {
        if (data == null) {
            return null;
        }
        String id = JSONUtil.getString(data, "id");
        String login = JSONUtil.getString(data, "login");
        String displayName = JSONUtil.getString(data, "display_name");
        String broadcasterType = JSONUtil.getString(data, "broadcaster_type");
        String description = JSONUtil.getString(data, "description");
        String profileImageUrl = JSONUtil.getString(data, "profile_image_url");
        long createdAt = DateTime.parseDatetime(JSONUtil.getString(data, "created_at"));
        int views = JSONUtil.getInteger(data, "view_count", 0);
        if (!StringUtil.isNullOrEmpty(id, login, displayName)) {
            return new UserInfo(id, login, displayName, broadcasterType, description, profileImageUrl, createdAt, views);
        }
        return null;
    }

}
