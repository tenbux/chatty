
package chatty.gui.components.settings;

/**
 *
 * @author tduva
 */
public interface LongSetting {
    Long getSettingValue();
    Long getSettingValue(Long def);
    void setSettingValue(Long setting);
}
