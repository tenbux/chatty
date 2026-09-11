
package chatty.gui.components.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the shared bit-flag checkbox panel logic that ModerationSettings,
 * LookSettings and TabSettings each used to duplicate: each checkbox's
 * selection state is stored as one bit of a Long setting value.
 */
public class BitFlagCheckboxSettingTest {

    private static final int FLAG_A = 1;
    private static final int FLAG_B = 2;
    private static final int FLAG_C = 4;

    private static class TestPanel extends BitFlagCheckboxSetting {
        int changedCount = 0;

        TestPanel() {
            addOption(FLAG_A, "A", null);
            addOption(FLAG_B, "B", null);
            addOption(FLAG_C, "C", null);
        }

        @Override
        protected void onOptionChanged() {
            changedCount++;
        }
    }

    @Test
    public void testGetSettingValue_noneSelected_returnsZero() {
        TestPanel panel = new TestPanel();
        assertEquals(Long.valueOf(0), panel.getSettingValue());
    }

    @Test
    public void testSetSettingValue_thenGetSettingValue_roundTrips() {
        TestPanel panel = new TestPanel();
        panel.setSettingValue((long) (FLAG_A | FLAG_C));

        assertTrue(panel.options.get(FLAG_A).isSelected());
        assertFalse(panel.options.get(FLAG_B).isSelected());
        assertTrue(panel.options.get(FLAG_C).isSelected());
        assertEquals(Long.valueOf(FLAG_A | FLAG_C), panel.getSettingValue());
    }

    @Test
    public void testGetSettingValueWithDefault_ignoresDefault_returnsActualValue() {
        TestPanel panel = new TestPanel();
        panel.setSettingValue((long) FLAG_B);

        assertEquals(Long.valueOf(FLAG_B), panel.getSettingValue(999L));
    }

    @Test
    public void testOnOptionChanged_calledOnProgrammaticSelection() {
        TestPanel panel = new TestPanel();
        panel.setSettingValue((long) FLAG_A);

        assertTrue("setSelected() on the checkboxes should have fired onOptionChanged() "
                + "at least once (this is what TabSettings.TabInfoOptions relies on to "
                + "keep its custom-color field in sync)", panel.changedCount > 0);
    }

}
