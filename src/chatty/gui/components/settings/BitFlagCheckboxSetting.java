
package chatty.gui.components.settings;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Base for a checkbox panel that stores each checkbox's selection state as a
 * single bit in a Long setting value, one bit-flag constant per checkbox.
 *
 * @author tduva
 */
abstract class BitFlagCheckboxSetting extends JPanel implements LongSetting {

    protected final Map<Integer, JCheckBox> options = new HashMap<>();

    protected JCheckBox addOption(int option, String text, String tip) {
        JCheckBox check = new JCheckBox(text);
        check.setToolTipText(SettingsUtil.addTooltipLinebreaks(tip));
        check.addItemListener(e -> onOptionChanged());
        options.put(option, check);
        return check;
    }

    /**
     * Called whenever any option checkbox's selection changes (including
     * programmatically through setSettingValue()). No-op by default;
     * subclasses can override to react, e.g. enable/disable a related
     * setting.
     */
    protected void onOptionChanged() {
    }

    @Override
    public Long getSettingValue() {
        long result = 0;
        for (Map.Entry<Integer, JCheckBox> entry : options.entrySet()) {
            if (entry.getValue().isSelected()) {
                result = result | entry.getKey();
            }
        }
        return result;
    }

    @Override
    public Long getSettingValue(Long def) {
        return getSettingValue();
    }

    @Override
    public void setSettingValue(Long setting) {
        for (Map.Entry<Integer, JCheckBox> entry : options.entrySet()) {
            entry.getValue().setSelected((setting & entry.getKey()) != 0);
        }
    }

}
