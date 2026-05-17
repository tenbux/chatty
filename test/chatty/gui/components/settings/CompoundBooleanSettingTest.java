
package chatty.gui.components.settings;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author tduva
 */
public class CompoundBooleanSettingTest {
    
    @Test
    public void test() {
        SimpleBooleanSetting check1 = new SimpleBooleanSetting("", "");
        SimpleBooleanSetting check2 = new SimpleBooleanSetting("", "");
        SimpleBooleanSetting check3 = new SimpleBooleanSetting("", "");
        SimpleBooleanSetting check4 = new SimpleBooleanSetting("", "");
        
        CompoundBooleanSetting all = new CompoundBooleanSetting(check1, check2, check3);
        CompoundBooleanSetting all2 = new CompoundBooleanSetting(check2, check3, check1);
        CompoundBooleanSetting all3 = new CompoundBooleanSetting(check1, check2, check3);
        CompoundBooleanSetting all4 = new CompoundBooleanSetting(check1, check2, check3, check4);
        assertEquals(0L, (long) all.getSettingValue());
        
        check1.setSettingValue(true);
        assertEquals(1L, (long) all.getSettingValue());
        
        all.setSettingValue(5L);
        assertTrue(check1.getSettingValue());
        assertFalse(check2.getSettingValue());
        assertTrue(check3.getSettingValue());
        
        check1.setSettingValue(true);
        check2.setSettingValue(true);
        check3.setSettingValue(false);
        assertEquals(3, (long) all.getSettingValue());
        
        all.setSettingValue(4L);
        assertEquals(4, (long) all.getSettingValue());
        
        all3.setSettingValue(3L);
        assertTrue(check1.getSettingValue());
        assertTrue(check2.getSettingValue());
        assertFalse(check3.getSettingValue());

        assertNotEquals(all.getSettingValue(), all2.getSettingValue());
        assertEquals(all.getSettingValue(), all3.getSettingValue());
        
        all4.setSettingValue(all.getSettingValue());
        assertTrue(check1.getSettingValue());
        assertTrue(check2.getSettingValue());
        assertFalse(check3.getSettingValue());
        assertFalse(check4.getSettingValue());
        
        check2.setSettingValue(false);
        
        all.setSettingValue(all4.getSettingValue());
        assertTrue(check1.getSettingValue());
        assertFalse(check2.getSettingValue());
        assertFalse(check3.getSettingValue());
        assertFalse(check4.getSettingValue());
    }
    
}
