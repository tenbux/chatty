
package chatty.gui.components.settings;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for the settings dialogs using a plain HashMap to build
 * combo box choices: since GenericComboSetting(Map) iterates the map's
 * keySet() to populate the combo box, a HashMap gives a JDK-dependent item
 * order instead of the order the choices were declared in. Several settings
 * dialogs (ChatSettings, CompletionSettings, FontSettings, IgnoreSettings,
 * LogSettings, NotificationSettings, TabSettings) were switched to
 * LinkedHashMap to fix this; this test locks in that a LinkedHashMap's
 * insertion order is preserved in the resulting combo box item order.
 */
public class GenericComboSettingTest {

    @Test
    public void testConstructor_linkedHashMap_preservesInsertionOrder() {
        Map<String, String> choices = new LinkedHashMap<>();
        choices.put("off", "Off");
        choices.put("timeout", "Timeout");
        choices.put("ban", "Ban");
        choices.put("delete", "Delete message");

        GenericComboSetting<String> combo = new GenericComboSetting<>(choices);

        assertEquals(4, combo.getItemCount());
        assertEquals("off", combo.getItemAt(0).value());
        assertEquals("timeout", combo.getItemAt(1).value());
        assertEquals("ban", combo.getItemAt(2).value());
        assertEquals("delete", combo.getItemAt(3).value());
    }

    @Test
    public void testAddData_linkedHashMap_appendsInInsertionOrder() {
        GenericComboSetting<String> combo = new GenericComboSetting<>();
        combo.add("first", "First");

        Map<String, String> more = new LinkedHashMap<>();
        more.put("second", "Second");
        more.put("third", "Third");
        combo.addData(more);

        assertEquals(3, combo.getItemCount());
        assertEquals("first", combo.getItemAt(0).value());
        assertEquals("second", combo.getItemAt(1).value());
        assertEquals("third", combo.getItemAt(2).value());
    }

}
