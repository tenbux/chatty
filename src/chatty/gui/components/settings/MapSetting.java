
package chatty.gui.components.settings;

import java.util.Map;

/**
 *
 * @author tduva
 */
public interface MapSetting<K, V> {

        Map<K, V> getSettingValue();

        void setSettingValue(Map<K, V> value);
}
