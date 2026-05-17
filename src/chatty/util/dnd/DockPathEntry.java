
package chatty.util.dnd;

import chatty.util.dnd.DockDropInfo.DropType;
import java.util.Objects;

/**
 *
 * @author tduva
 */
public record DockPathEntry(Type type, DropType location, int index, String id) {

    public enum Type {
        SPLIT, TAB, POPOUT
    }

    private DockPathEntry(DropType location) {
        this(Type.SPLIT, location, -1, null);
    }

    private DockPathEntry(int index) {
        this(Type.TAB, null, index, null);
    }

    private DockPathEntry(String id) {
        this(Type.POPOUT, null, -1, id);
    }

    public static DockPathEntry createSplit(DropType location) {
        return new DockPathEntry(location);
    }

    public static DockPathEntry createTab(int index) {
        return new DockPathEntry(index);
    }

    public static DockPathEntry createPopout(String popoutId) {
        return new DockPathEntry(popoutId);
    }

    @Override
    public String toString() {
        return switch (type) {
            case POPOUT -> String.format("%s (%s)", type, id);
            case SPLIT -> String.format("%s (%s)", type, location);
            case TAB -> String.format("%s (%s)", type, index);
        };
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
        final DockPathEntry other = (DockPathEntry) obj;
        if (this.index != other.index) {
            return false;
        }
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        return this.location == other.location;
    }

}
