
package chatty.util.dnd;

import java.util.List;

/**
 * 
 * 
 * @author tduva
 */
public interface DockListener {
    
    /**
     * The active content changed, probably due to a focus change.
     * 
     * @param popout The popout, or null if in main DockBase
     * @param content The content (never null)
     * @param focusChange Whether this change was based on a focus event
     */
    void activeContentChanged(DockPopout popout, DockContent content, boolean focusChange);
    void popoutOpened(DockPopout popout, DockContent content);
    void popoutClosed(DockPopout popout, List<DockContent> content);
    void contentAdded(DockContent content);
    void contentRemoved(DockContent content);
    void popoutClosing(DockPopout popout);
    
}
