
package chatty.gui.components;

import chatty.util.api.StreamInfoHistoryItem;

/**
 *
 * @author tduva
 */
public interface ViewerHistoryListener {

    void itemSelected(StreamInfoHistoryItem item);
    void noItemSelected();
}