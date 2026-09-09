# OS System Sounds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users pick an OS-provided alert sound (or a universal "System Beep") from the existing notification sound dropdowns, so a working sound is always available even when no custom `.wav` file is configured or the configured one is missing.

**Architecture:** A new pure-logic utility, `chatty.util.SystemSounds`, exposes the OS-provided sounds as `displayName -> absolutePath` (empty on unsupported platforms) plus a `BEEP` sentinel constant. Two existing GUI classes (`NotificationSettings`, `NotificationEditor`) add these as extra entries in their existing sound-file combo boxes. Three existing playback call sites (`NotificationManager.playSound()` and the two "test sound" buttons) each get a one-line branch: play `Toolkit.beep()` for the sentinel, otherwise fall through to the existing, unmodified `Sound.play(...)` call.

**Tech Stack:** Java 21, Swing, `javax.sound.sampled` (already supports WAV/AIFF natively, no new dependency), JUnit 4.

## Global Constraints

- No new dependency: `javax.sound.sampled`'s built-in AIFF decoder is used as-is.
- No MP3/OGG support, no change to the custom sounds-folder format, no Linux system-sound enumeration (all out of scope per the design doc, `docs/superpowers/specs/2026-09-09-os-system-sounds-design.md`).
- `SystemSounds` must have no Swing/AWT dependency (pure `java.io`/`java.nio.file` logic), so it stays independently testable.
- Java 21 source/target compatibility (project-wide, `build.gradle`).
- No em dashes anywhere (code comments, doc comments, commit messages). Use a comma, colon, semicolon, parentheses, or restructure the sentence.
- US English spelling everywhere.
- Booleans read positively (`isValid`, not `isNotDisabled`); no reserved words as identifiers.
- Never silently swallow exceptions without logging or rethrowing, except where matching an existing established pattern in code being modified (the existing `NotificationManager.playSound()` catch block already does this deliberately; do not change that behavior).
- Match existing code style: this codebase's Java doc-comment convention on public classes is a short purpose sentence plus `@author tduva`, even on brand-new files (confirmed by checking `src/chatty/util/history/OutageBackfillManager.java`, added in a recent commit). Follow it for `SystemSounds.java`.
- This codebase's existing JUnit test method naming convention is `testDoesThing()` (see `test/chatty/util/MiscUtilTest.java`), not `test_method_scenario_result`. Follow the codebase's existing convention for consistency with the ~30 other test files in `test/chatty/util/`.

---

### Task 1: `SystemSounds` utility

**Files:**
- Create: `src/chatty/util/SystemSounds.java`
- Test: `test/chatty/util/SystemSoundsTest.java`

**Interfaces:**
- Produces: `SystemSounds.BEEP` (`public static final String`), the sentinel value used everywhere else in this plan to mean "play the OS beep instead of a file."
- Produces: `SystemSounds.get()` (`public static Map<String, Path> get()`), returns OS-provided alert sounds as display name to absolute `Path`, sorted alphabetically by name; empty `Map` on unsupported platforms or unreadable directories. Later tasks call this directly, no other class wraps it.

- [ ] **Step 1: Write the failing tests**

Create `test/chatty/util/SystemSoundsTest.java`:

```java

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
```

`testGetOnMacReturnsBuiltInSounds` uses `assumeTrue` so it's skipped (not failed) on a non-Mac test runner; `/System/Library/Sounds/Glass.aiff` is a stable, long-standing macOS fixture safe to assert on directly.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "chatty.util.SystemSoundsTest"`
Expected: FAIL to compile, `SystemSounds` does not exist.

- [ ] **Step 3: Write the implementation**

Create `src/chatty/util/SystemSounds.java`:

```java

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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "chatty.util.SystemSoundsTest"`
Expected: PASS (2 tests; on a non-Mac runner, `testGetOnMacReturnsBuiltInSounds` reports as skipped, not failed).

- [ ] **Step 5: Commit**

```bash
git add src/chatty/util/SystemSounds.java test/chatty/util/SystemSoundsTest.java
git commit -m "Add SystemSounds utility for OS-provided alert sounds"
```

---

### Task 2: Wire the Beep sentinel into real notification playback

**Files:**
- Modify: `src/chatty/gui/notifications/NotificationManager.java:396-417` (`playSound` method)

**Interfaces:**
- Consumes: `SystemSounds.BEEP` (`String`, from Task 1).

**Note on testing:** `NotificationManager` has no existing unit tests in this codebase (confirmed: no `test/chatty/gui/notifications/NotificationManagerTest.java` exists) and its constructor requires `MainGui`, `Addressbook`, and `ChannelFavorites`, none of which are practical to construct in a unit test. Consistent with the rest of this GUI layer, this task is verified by compilation plus the end-to-end manual check in Task 5; do not add mocking infrastructure to force a unit test here, that would be new scope beyond this feature.

- [ ] **Step 1: Add the Beep branch**

In `src/chatty/gui/notifications/NotificationManager.java`, add these two imports near the other `java.*`/`chatty.util.*` imports:

```java
import chatty.util.SystemSounds;
```

```java
import java.awt.Toolkit;
```

Then change `playSound()` from:

```java
    private void playSound(Notification n) {
        // Check stuff, return true only if played
        if (!settings.getBoolean("sounds")) {
            return;
        }
        if (n.lastPlayedAgo() < n.soundCooldown* 1000L) {
            return;
        }
        if (n.lastMatchedAgo() < n.soundInactiveCooldown* 1000L) {
            return;
        }
        n.setSoundPlayed();
        
        Chatty.updateCustomPathFromSettings(Chatty.PathType.SOUND);
        Path soundsPath = Chatty.getPath(Chatty.PathType.SOUND);
        Path path = soundsPath.resolve(n.soundFile);
        try {
            Sound.play(path, n.soundVolume, "notification_"+n.type.toString(), 0);
        } catch (Exception ex) {
            // Do nothing further (already logged)
        }
    }
```

to:

```java
    private void playSound(Notification n) {
        // Check stuff, return true only if played
        if (!settings.getBoolean("sounds")) {
            return;
        }
        if (n.lastPlayedAgo() < n.soundCooldown* 1000L) {
            return;
        }
        if (n.lastMatchedAgo() < n.soundInactiveCooldown* 1000L) {
            return;
        }
        n.setSoundPlayed();
        
        if (SystemSounds.BEEP.equals(n.soundFile)) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        
        Chatty.updateCustomPathFromSettings(Chatty.PathType.SOUND);
        Path soundsPath = Chatty.getPath(Chatty.PathType.SOUND);
        Path path = soundsPath.resolve(n.soundFile);
        try {
            Sound.play(path, n.soundVolume, "notification_"+n.type.toString(), 0);
        } catch (Exception ex) {
            // Do nothing further (already logged)
        }
    }
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/chatty/gui/notifications/NotificationManager.java
git commit -m "Play system beep for notifications when the beep sentinel is selected"
```

---

### Task 3: Wire system sounds into `NotificationSettings` (master Test/Play panel)

**Files:**
- Modify: `src/chatty/gui/components/settings/NotificationSettings.java:240-254` (Play button), `:297-308` (Sound OS Command test), `:444-454` (`scanFiles` combo population)

**Interfaces:**
- Consumes: `SystemSounds.BEEP` (`String`), `SystemSounds.get()` (`Map<String, Path>`), both from Task 1.

**Note on testing:** Same as Task 2, this is Swing GUI code with no existing unit test coverage in this codebase (`NotificationSettings` has no test file). Verified by compilation here; the end-to-end manual check is Task 5.

- [ ] **Step 1: Add the import**

In `src/chatty/gui/components/settings/NotificationSettings.java`, add near the other `chatty.util.*` imports:

```java
import chatty.util.SystemSounds;
```

(`java.awt.*` is already imported at the top of this file, so `Toolkit` needs no new import.)

- [ ] **Step 2: Populate the combo with Beep + system sounds in `scanFiles()`**

Change this block (inside the `else` branch, after the `files == null` check):

```java
            String[] fileNames = new String[files.length];
            for (int i=0;i<files.length;i++) {
                fileNames[i] = files[i].getName();
            }
            Arrays.sort(fileNames);
            editor.setSoundFiles(path, fileNames);
            soundFiles.clear();
            for (String fileName : fileNames) {
                soundFiles.add(fileName);
            }
```

to:

```java
            String[] fileNames = new String[files.length];
            for (int i=0;i<files.length;i++) {
                fileNames[i] = files[i].getName();
            }
            Arrays.sort(fileNames);
            editor.setSoundFiles(path, fileNames);
            soundFiles.clear();
            soundFiles.add(SystemSounds.BEEP, "System Beep");
            for (Map.Entry<String, Path> entry : SystemSounds.get().entrySet()) {
                soundFiles.add(entry.getValue().toString(), "System: " + entry.getKey());
            }
            for (String fileName : fileNames) {
                soundFiles.add(fileName);
            }
```

- [ ] **Step 3: Branch on Beep in the "Play" button**

Change:

```java
        JButton playSound = new JButton("Play");
        playSound.addActionListener(e -> {
            try {
                String file = soundFiles.getSettingValue();
                if (file != null && !file.isEmpty()) {
                    long volume = volumeSlider.getSettingValue();
                    Sound.play(soundsPath.getCurrentPath().resolve(file), volume, "test", -1);
                }
            }
            catch (Exception ex) {
                GuiUtil.showNonModalMessage(d, "Error Playing Sound",
                        ex.toString(),
                        JOptionPane.ERROR_MESSAGE);
            }
        });
```

to:

```java
        JButton playSound = new JButton("Play");
        playSound.addActionListener(e -> {
            try {
                String file = soundFiles.getSettingValue();
                if (file != null && !file.isEmpty()) {
                    if (SystemSounds.BEEP.equals(file)) {
                        Toolkit.getDefaultToolkit().beep();
                        return;
                    }
                    long volume = volumeSlider.getSettingValue();
                    Sound.play(soundsPath.getCurrentPath().resolve(file), volume, "test", -1);
                }
            }
            catch (Exception ex) {
                GuiUtil.showNonModalMessage(d, "Error Playing Sound",
                        ex.toString(),
                        JOptionPane.ERROR_MESSAGE);
            }
        });
```

- [ ] **Step 4: Skip the Beep sentinel in the "Sound OS Command" test**

Change:

```java
                    String result = "No sound played, no file found";

                    String file = soundFiles.getSettingValue();
                    if (file != null && !file.isEmpty()) {
                        CustomCommand command = CustomCommand.parse(value);
                        long volume = volumeSlider.getSettingValue();
                        result = Sound.get().runCommand(command, soundsPath.getCurrentPath().resolve(file), volume);
                    }
                    JOptionPane.showMessageDialog(component, result);
                    return null;
```

to:

```java
                    String result = "No sound played, no file found";

                    String file = soundFiles.getSettingValue();
                    if (file != null && !file.isEmpty() && !SystemSounds.BEEP.equals(file)) {
                        CustomCommand command = CustomCommand.parse(value);
                        long volume = volumeSlider.getSettingValue();
                        result = Sound.get().runCommand(command, soundsPath.getCurrentPath().resolve(file), volume);
                    }
                    JOptionPane.showMessageDialog(component, result);
                    return null;
```

- [ ] **Step 5: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/chatty/gui/components/settings/NotificationSettings.java
git commit -m "Add system sounds and beep entries to the notification Test sounds panel"
```

---

### Task 4: Wire system sounds into `NotificationEditor` (per-notification Sound dropdown)

**Files:**
- Modify: `src/chatty/gui/components/settings/NotificationEditor.java:477-490` (Test Sound button), `:776-783` (`setSoundFiles`)

**Interfaces:**
- Consumes: `SystemSounds.BEEP` (`String`), `SystemSounds.get()` (`Map<String, Path>`), both from Task 1.

**Note on testing:** Same as Tasks 2 and 3, no existing unit test coverage for this GUI class. Verified by compilation here; end-to-end manual check is Task 5.

- [ ] **Step 1: Add the import**

In `src/chatty/gui/components/settings/NotificationEditor.java`, add near the other `chatty.util.*` imports:

```java
import chatty.util.SystemSounds;
```

(`java.awt.*` is already imported at the top of this file, so `Toolkit` needs no new import.)

- [ ] **Step 2: Populate the combo with Beep + system sounds in `setSoundFiles()`**

Change:

```java
        public void setSoundFiles(Path path, String[] names) {
            soundFile.removeAllItems();
            soundFile.add((String)null, "<None>");
            for (String name : names) {
                soundFile.add(name);
            }
            this.soundsPath = path;
        }
```

to:

```java
        public void setSoundFiles(Path path, String[] names) {
            soundFile.removeAllItems();
            soundFile.add((String)null, "<None>");
            soundFile.add(SystemSounds.BEEP, "System Beep");
            for (Map.Entry<String, Path> entry : SystemSounds.get().entrySet()) {
                soundFile.add(entry.getValue().toString(), "System: " + entry.getKey());
            }
            for (String name : names) {
                soundFile.add(name);
            }
            this.soundsPath = path;
        }
```

- [ ] **Step 3: Branch on Beep in the "Test Sound" button**

Change:

```java
            playSound = new JButton("Test Sound");
            playSound.addActionListener(e -> {
                try {
                    String file = soundFile.getSettingValue();
                    if (file != null && !file.isEmpty()) {
                        long volume = volumeSlider.getSettingValue();
                        Sound.play(soundsPath.resolve(file), volume, "test", -1);
                    }
                } catch (Exception ex) {
                    GuiUtil.showNonModalMessage(dialog, "Error Playing Sound",
                            ex.toString(),
                            JOptionPane.ERROR_MESSAGE);
                }
            });
```

to:

```java
            playSound = new JButton("Test Sound");
            playSound.addActionListener(e -> {
                try {
                    String file = soundFile.getSettingValue();
                    if (file != null && !file.isEmpty()) {
                        if (SystemSounds.BEEP.equals(file)) {
                            Toolkit.getDefaultToolkit().beep();
                            return;
                        }
                        long volume = volumeSlider.getSettingValue();
                        Sound.play(soundsPath.resolve(file), volume, "test", -1);
                    }
                } catch (Exception ex) {
                    GuiUtil.showNonModalMessage(dialog, "Error Playing Sound",
                            ex.toString(),
                            JOptionPane.ERROR_MESSAGE);
                }
            });
```

- [ ] **Step 4: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/chatty/gui/components/settings/NotificationEditor.java
git commit -m "Add system sounds and beep entries to the per-notification Sound dropdown"
```

---

### Task 5: Full build, test suite, and manual verification

**Files:** none (verification only)

**Interfaces:** none, this task consumes the finished feature end-to-end.

This project's Swing GUI cannot be driven or screenshotted by this session's tools (there is no macOS desktop-automation tool available here, only iOS Simulator and browser automation, neither of which apply to a native Swing app). This task builds and deploys the app and hands off a short, explicit manual check to the user; it must not be reported as done until that check comes back confirmed.

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass (including the two new `SystemSoundsTest` tests from Task 1).

- [ ] **Step 2: Build and deploy the Mac app**

Follow the `chatty-deploy` skill (quit Chatty, `./gradlew macBuild`, replace `~/Applications/Chatty.app`, relaunch), per the project's Mac App Build & Deploy instructions in `CLAUDE.md`.

- [ ] **Step 3: Ask the user to manually verify**

Ask the user to do the following in the running app and report back what they see:

1. Open Settings, go to Notifications, Sound tab.
2. Confirm the "File:" dropdown in "Test sounds / Output Device" now lists "System Beep" and 14 "System: <Name>" entries (Basso, Blow, Bottle, Frog, Funk, Glass, Hero, Morse, Ping, Pop, Purr, Sosumi, Submarine, Tink) ahead of any custom `.wav` files.
3. Select "System Beep", click Play: expect the standard macOS alert beep.
4. Select "System: Glass" (or any other system entry), click Play: expect that macOS alert sound to play.
5. Open the Events tab, edit (or create) a notification rule, e.g. Highlight, and confirm its "Sound" dropdown also lists the same Beep and System entries, and Test Sound plays them correctly.
6. Trigger a real highlight (or whichever notification type was set to a system sound) and confirm the system sound actually plays on the real notification path, not just the test buttons.

Do not report this task, or the feature, as complete until the user confirms all six checks above.
