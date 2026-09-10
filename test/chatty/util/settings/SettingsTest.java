
package chatty.util.settings;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for Settings#putList()'s change-detection: it must
 * correctly report "unchanged" for both List-backed and Set-backed list
 * settings, not just List-backed ones. Set#equals() against something that
 * isn't itself a Set always returns false regardless of content, so naively
 * comparing a Set-backed setting's value to a fresh ArrayList would report
 * "changed" unconditionally.
 */
public class SettingsTest {

    private static Settings makeSettings() {
        return new Settings(null, null);
    }

    @Test
    public void testPutList_listBacked_reportsUnchangedForSameContentAndOrder() {
        Settings settings = makeSettings();
        settings.addList("items", new java.util.ArrayList<>(), Setting.STRING);
        settings.putList("items", Arrays.asList("a", "b", "c"));

        assertFalse("identical content and order must report unchanged",
                settings.putList("items", Arrays.asList("a", "b", "c")));
    }

    @Test
    public void testPutList_listBacked_reportsChangedForDifferentOrder() {
        Settings settings = makeSettings();
        settings.addList("items", new java.util.ArrayList<>(), Setting.STRING);
        settings.putList("items", Arrays.asList("a", "b", "c"));

        // Order matters for a genuine list setting.
        assertTrue("different order must report changed",
                settings.putList("items", Arrays.asList("c", "b", "a")));
    }

    @Test
    public void testPutList_setBacked_reportsUnchangedForSameContentDifferentOrder() {
        Settings settings = makeSettings();
        // Mirrors how e.g. "scopes" is registered: a Set, not a List.
        settings.addList("scopes", new HashSet<>(), Setting.STRING);
        settings.putList("scopes", Arrays.asList("chat:read", "chat:edit"));

        // Set order is not meaningful, and the set implementation may not
        // preserve insertion order at all - same elements, different
        // encounter order must still report unchanged.
        assertFalse("same elements regardless of order must report unchanged for a Set-backed setting",
                settings.putList("scopes", Arrays.asList("chat:edit", "chat:read")));
    }

    @Test
    public void testPutList_setBacked_reportsChangedForDifferentContent() {
        Settings settings = makeSettings();
        settings.addList("scopes", new LinkedHashSet<>(), Setting.STRING);
        settings.putList("scopes", Arrays.asList("chat:read", "chat:edit"));

        assertTrue("different content must report changed",
                settings.putList("scopes", Arrays.asList("chat:read")));
    }

}
