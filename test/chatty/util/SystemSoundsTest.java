
package chatty.util;

import org.junit.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 *
 * @author tduva
 */
public class SystemSoundsTest {

    @Test
    public void testGetOnUnsupportedOsReturnsEmpty() {
        String original = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Linux");
            assertEquals(0, SystemSounds.get().size());
        } finally {
            System.setProperty("os.name", original);
        }
    }

    @Test
    public void testGetOnMacReturnsBuiltInSounds() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("mac"));

        Map<String, Path> sounds = SystemSounds.get();

        assertFalse(sounds.isEmpty());
        assertTrue(sounds.containsKey("Glass"));
        for (Path path : sounds.values()) {
            assertTrue(path.isAbsolute());
            assertTrue(path.toFile().isFile());
        }
    }

}
