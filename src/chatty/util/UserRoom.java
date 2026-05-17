
package chatty.util;

import chatty.Room;
import chatty.User;
import java.util.Objects;

/**
 * A room, with an optional user attached.
 *
 * @param room The room, required.
 * @param user May be null, only if user already exists.
 * @author tduva
 */
public record UserRoom(Room room, User user) implements Comparable<UserRoom> {

    @Override
    public int compareTo(UserRoom o) {
        if (Objects.equals(this, o)) {
            return 0;
        }
        if (o == null || o.room.getChannel() == null) {
            return -1;
        }
        return -o.room.getChannel().compareTo(this.room.getChannel());
    }

}
