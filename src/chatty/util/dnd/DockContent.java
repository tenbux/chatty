
package chatty.util.dnd;

import java.awt.Color;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;

/**
 * This holds the component that is the actually visible content and provides
 * various meta information and methods related to the content.
 * 
 * @author tduva
 */
public interface DockContent {
    
    /**
     * The component that will be added to the layout.
     * 
     * @return 
     */
    JComponent getComponent();
    
    /**
     * The title (used e.g. for tab names). Should be rather short and usually
     * not change (but it can).
     * 
     * @return 
     */
    String getTitle();
    
    String getLongTitle();
    
    void setLongTitle(String title);
    
    DockPath getPath();
    
    String getId();
    
    void setId(String id);
    
    void setTargetPath(DockPath path);
    
    DockPath getTargetPath();
    
    void setDockParent(DockChild parent);
    
    /**
     * The context menu for the tab.
     * 
     * @return The menu, can be null to show no menu
     */
    JPopupMenu getContextMenu();
    
    /**
     * Provides a custom tab component.
     * 
     * @return The tab component, can be null to use the default
     */
    DockTabComponent getTabComponent();
    
    /**
     * This can be called to remove the content. Was exactly is performed may
     * depend on the component, but commonly this should call the DockManager to
     * remove the content.
     */
    void remove();
    void addListener(DockContentPropertyListener listener);
    void removeListener(DockContentPropertyListener listener);
    Color getForegroundColor();
    boolean canPopout();
    
    interface DockContentPropertyListener {
        
        enum Property {
            TITLE, LONG_TITLE, FOREGROUND
        }
        
        void propertyChanged(Property property, DockContent content);
    }
    
}
