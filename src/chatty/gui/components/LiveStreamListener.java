
package chatty.gui.components;

import chatty.util.api.StreamInfo;
import java.util.Collection;

/**
 *
 * @author tduva
 */
public interface LiveStreamListener {
    void liveStreamClicked(Collection<StreamInfo> selectedStreams);
}
