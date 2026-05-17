
package chatty.gui.components.settings;

import java.util.List;

/**
 *
 * @author tduva
 * @param <T>
 */
public interface ListSetting<T> {
    List<T> getSettingValue();
    void setSettingValue(List<T> value);
}
