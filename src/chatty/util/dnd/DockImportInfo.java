
package chatty.util.dnd;

import javax.swing.*;
import java.awt.*;

/**
 * Information for a potential import, which helps decide a component if a drop
 * can occur. It combines the transferable (containing info from the component
 * where the drag movement started) and the support info (which is provided by
 * the drag&drop system and contains info like the potential drop coordinates).
 *
 * @author tduva
 */
public record DockImportInfo(TransferHandler.TransferSupport info, DockTransferable tf) {

    /**
     * Gets the drop location relative to the given component.
     *
     * @param comp
     * @return
     */
    public Point getLocation(Component comp) {
        return SwingUtilities.convertPoint(info.getComponent(), info.getDropLocation().getDropPoint(), comp);
    }

    public String toString() {
        return String.format("DIF(%s,%s)",
                info, tf);
    }

}
