
package chatty.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Looks up sound files built into the operating system, so a working
 * notification sound is always available even if no custom sound file has
 * been configured.
 *
 * @author tduva
 */
public class SystemSounds {

    /** Sentinel value stored/selected for the universal system beep. */
    public static final String BEEP = "$system_beep$";

    /**
     * Returns the OS-provided alert sounds available on this system, as
     * display name to absolute file path, sorted alphabetically by name.
     * Returns an empty map on operating systems this doesn't support (e.g.
     * Linux), or if the expected system folder can't be read.
     */
    public static Map<String, Path> get() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return scan(Paths.get("/System/Library/Sounds"), ".aiff");
        }
        if (os.contains("win")) {
            String root = System.getenv("SystemRoot");
            return scan(Paths.get(root != null ? root : "C:\\Windows", "Media"), ".wav");
        }
        return Collections.emptyMap();
    }

    /**
     * Returns the label to display for a stored sound file value, such as
     * "System Beep" or "System: Glass" for OS-provided sounds, or the value
     * unchanged if it's a custom sound file.
     */
    public static String getDisplayName(String soundFile) {
        if (BEEP.equals(soundFile)) {
            return "System Beep";
        }
        for (Map.Entry<String, Path> entry : get().entrySet()) {
            if (entry.getValue().toString().equals(soundFile)) {
                return "System: " + entry.getKey();
            }
        }
        return soundFile;
    }

    private static Map<String, Path> scan(Path dir, String extension) {
        File[] files = dir.toFile().listFiles((d, name) -> name.toLowerCase().endsWith(extension));
        if (files == null) {
            return Collections.emptyMap();
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        Map<String, Path> result = new LinkedHashMap<>();
        for (File file : files) {
            String name = file.getName();
            String displayName = name.substring(0, name.length() - extension.length());
            result.put(displayName, file.toPath());
        }
        return result;
    }

}
