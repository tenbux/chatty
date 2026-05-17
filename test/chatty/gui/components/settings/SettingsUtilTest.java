
package chatty.gui.components.settings;

import org.junit.Test;

import static chatty.gui.components.settings.SettingsUtil.removeHtmlConditions;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author tduva
 */
public class SettingsUtilTest {
    
    @Test
    public void testRemoveHtmlConditions() {
        assertEquals("ab",
                removeHtmlConditions(
                        "a<!--#START:streamStatus#-->___<!--#END:streamStatus#-->b", "stream"));
        assertEquals("ab",
                removeHtmlConditions(
                        "a<!--#START:streamStatus#-->___<!--#END:streamStatus#-->b", "streamStatusAbc"));
        assertEquals("a<!--#START:streamStatus#-->___<!--#END:streamStatus#-->b",
                removeHtmlConditions(
                        "a<!--#START:streamStatus#-->___<!--#END:streamStatus#-->b", "streamStatus"));
        assertEquals("ab",
                removeHtmlConditions(
                        "a<!--#START:abc#-->___<!--#END:abc#-->b", "streamStatus"));
        assertEquals("a<!--#START:!streamStatus#-->___<!--#END:!streamStatus#-->b",
                removeHtmlConditions(
                        "a<!--#START:!streamStatus#-->___<!--#END:!streamStatus#-->b", "stream"));
        assertEquals("ab",
                removeHtmlConditions(
                        "a<!--#START:!streamStatus#-->___<!--#END:!streamStatus#-->b", "streamStatus"));
        assertEquals("a<!--#START:a#-->___<!--#END:a#-->b<!--#START:a#-->___<!--#END:a#-->c",
                removeHtmlConditions(
                        "a<!--#START:a#-->___<!--#END:a#-->b<!--#START:a#-->___<!--#END:a#-->c", "a"));
        assertEquals("abc",
                removeHtmlConditions(
                        "a<!--#START:a#-->___<!--#END:a#-->b<!--#START:a#-->___<!--#END:a#-->c", "b"));
        assertEquals("a<!--#START:a#-->__<!--#END:a#-->b",
                removeHtmlConditions(
                        "a<!--#START:a#-->_<!--#START:b#-->_<!--#END:b#-->_<!--#END:a#-->b", "a"));
        assertEquals("ab",
                removeHtmlConditions(
                        "a<!--#START:a#-->_<!--#START:b#-->_<!--#END:b#-->_<!--#END:a#-->b", "b"));
    }
    
}
