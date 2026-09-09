# OS-provided system sounds for notifications

Date: 2026-09-09
Status: Draft, awaiting approval

## Problem

Chatty's notification sounds (highlight, message, join/part, follower, etc.)
require a `.wav` file to exist in the user's configured sounds folder
(`soundsPath` setting, default from `Chatty.PathType.SOUND`). If the selected
file is missing or invalid, `NotificationManager.playSound()`
(`src/chatty/gui/notifications/NotificationManager.java:396`) silently
swallows the error and nothing plays; no fallback of any kind exists today,
not even a system beep.

Concretely: the user's `ding.wav` file went missing, and the "Sound" dropdown
in Notification Settings shows only the missing filename or nothing usable.
They want to be able to pick a built-in OS alert sound directly from that
dropdown instead of having to go find/restore a `.wav` file first.

## Goal

Add OS-provided sounds as selectable entries in the existing sound-file
dropdowns, alongside the user's custom `.wav` files, so a working sound is
always available without requiring any file management. This should not
introduce platform-specific code that leaves other platforms broken or
worse off than today.

Out of scope: MP3/OGG support, changing the custom sounds-folder format,
Linux system-sound enumeration (no dependency-free, cross-distro way to do
this, see Platform behavior below).

## Current mechanism (reference)

- `src/chatty/util/Sound.java` plays sounds via `javax.sound.sampled`.
  `createClip()` (line 143) calls `AudioSystem.getAudioInputStream(file)`,
  which auto-detects the format using the JDK's bundled decoders (WAV, AU,
  and AIFF are all supported out of the box, so no new dependency is needed
  to play macOS's `.aiff` alert sounds).
- `NotificationManager.playSound()` (line 396) resolves
  `soundsPath.resolve(n.soundFile)` and calls `Sound.play(path, ...)` inside
  a try/catch that does nothing on failure.
- Two GUI locations populate the "which sound file" dropdown, both via
  `ComboStringSetting`, which already supports separate stored-value vs.
  displayed-label pairs (used today for the `<None>` entry, value `null`,
  label `"<None>"`):
  - `NotificationSettings.scanFiles()` (`src/chatty/gui/components/settings/NotificationSettings.java:420`)
    populates the master "Test sounds" combo (`soundFiles`) and calls
    `editor.setSoundFiles(path, fileNames)`.
  - `NotificationEditor.setSoundFiles()` (`src/chatty/gui/components/settings/NotificationEditor.java:776`)
    populates the per-notification-type "Sound" combo used when editing an
    individual notification rule (highlight, message, join/part, etc.).
- Both are fed by `WavFilenameFilter` (`NotificationSettings.java:478`),
  which lists `*.wav` files in the configured sounds folder.
- A key `Path` fact this design relies on: `Path.resolve(String other)`
  trivially returns `other` unchanged if `other` parses to an absolute path.
  So `soundsPath.resolve(n.soundFile)` already does the right thing if
  `n.soundFile` happens to be an absolute path string; no resolution-logic
  change is needed for system sounds.

## Design

### New utility: `SystemSounds`

A new class, `src/chatty/util/SystemSounds.java`, pure static, no Swing
dependency:

```java
public class SystemSounds {
    /** Sentinel stored/selected value for the universal beep entry. */
    public static final String BEEP = "$system_beep$";

    /** Returns display-name -> absolute Path for OS-provided alert sounds,
     *  sorted alphabetically by display name. */
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
        // Returns empty map if dir doesn't exist or isn't readable,
        // mirroring the existing "files == null" handling in scanFiles().
    }
}
```

- macOS: `/System/Library/Sounds/*.aiff`, the same fixed set of about 14
  sounds shown in System Settings, Sound, Alert sound (Basso, Glass, Ping,
  Sosumi, etc.).
- Windows: `%SystemRoot%\Media\*.wav` (falls back to `C:\Windows\Media` if
  the env var is somehow unset).
- Any other OS (Linux, unknown): returns an empty map. No platform-specific
  code path runs, nothing errors, the dropdown just doesn't gain extra
  entries beyond the universal Beep below: functionally identical to
  today's behavior for those platforms, not worse.
- A missing/unreadable directory on Mac/Windows is treated the same as "no
  files found," not an error.

### Universal Beep entry

One additional fixed entry, always present regardless of OS: `"System
Beep"`, stored as the `SystemSounds.BEEP` sentinel string. Played via
`Toolkit.getDefaultToolkit().beep()`, plain AWT, works identically on
every platform, and is the only "system sound" Linux users get in this
pass. This is what guarantees the feature never becomes Mac-only in
practice.

### GUI integration

Both dropdown-populating methods get the same additive change: insert,
after the existing `<None>` entry and before the custom `.wav` file list:

1. One entry: value `SystemSounds.BEEP`, label `"System Beep"`.
2. One entry per `SystemSounds.get()` result, in the sorted order `get()`
   returns: value equals the absolute path string, label equals
   `"System: " + name`.

No change to `ComboStringSetting` itself; it already supports this via
`add(value, label)`. No change to the custom-folder scan or
`WavFilenameFilter`.

### Playback

Three call sites currently resolve a stored `soundFile` string and play it;
each gets one early-branch check before its existing logic, which is
otherwise untouched:

1. `NotificationManager.playSound()` (line 396), the real notification
   playback path.
2. The "Play" button in `NotificationSettings`'s "Test sounds" panel (line
   around 240).
3. The "Test Sound" button in `NotificationEditor` (line around 478).

Branch: `if (SystemSounds.BEEP.equals(soundFile)) { Toolkit.getDefaultToolkit().beep(); return; }`,
otherwise fall through to the existing `Sound.play(soundsPath.resolve(soundFile), ...)`
call unmodified. Because system-sound values are absolute paths,
`resolve()` returns them as-is, so that existing call already handles them
correctly with zero changes.

The separate "Sound OS Command" test button (`NotificationSettings.java:297`,
runs an external program with the file as a parameter) has no meaningful
behavior for a beep since there's no file to hand an external command. It
simply no-ops (shows the existing "No sound played, no file found" message)
when the Beep sentinel is selected.

### Error handling

Directory-listing failures in `SystemSounds.scan()` return an empty map
rather than throwing, mirroring the existing `files == null` handling
already in `NotificationSettings.scanFiles()`. This whole feature is
additive to the dropdown contents plus an early-return branch ahead of
existing playback code; it cannot regress current behavior for any
platform or for existing custom `.wav` selections.

### Testing

- Unit test for `SystemSounds.get()`: verify it returns a non-empty map
  when run on macOS (asserting on actual `/System/Library/Sounds` content,
  which is a stable, long-standing OS fixture), and returns an empty map
  when the OS name doesn't match `"mac"` or `"win"` (inject a fake
  `os.name` value rather than relying on the actual test-runner OS, so the
  "unsupported OS" branch is exercised deterministically on any CI/dev
  machine).
- Manual check on this Mac: dropdown shows `System Beep` plus 14 `System:
  <Name>` entries; selecting one and clicking Test/Play plays it; selecting
  `System Beep` triggers a beep; existing custom `.wav` files still list
  and play exactly as before.
