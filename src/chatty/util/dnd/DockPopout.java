
package chatty.util.dnd;

import java.awt.Window;
import java.util.Collection;

/**
 *
 * @author tduva
 */
public interface DockPopout {

    Window getWindow();
    
    DockBase getBase();
    
    void setTitle(String title);
    
    String getId();
    
    void setId(String id);
    
    /**
     * Sets an id, avoiding ids that are already in use. Must be called before
     * the popout is used or reused.
     * 
     * @param inUse 
     */
    void setId(Collection<DockPopout> inUse);
    
    static String makeId(String prefix, Collection<DockPopout> existingPopouts) {
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            String attempt = prefix+i;
            if (!idUsed(attempt, existingPopouts)) {
                return attempt;
            }
        }
        return null;
    }
    
    static boolean idUsed(String id, Collection<DockPopout> existingPopouts) {
        for (DockPopout p : existingPopouts) {
            if (p.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }
    
}
