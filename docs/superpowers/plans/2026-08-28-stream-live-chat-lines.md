# Stream Live/Offline Chat Lines Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Print an explicit `** <Name> is now LIVE **` / `** <Name> is now OFFLINE **` line in a joined channel when its stream actually changes online state, so the event is distinguishable in chat and in the on-disk log.

**Architecture:** A new `StreamLiveTracker` holds the last known online state per stream and reports transitions. `TwitchClient.MyStreamInfoListener.streamInfoUpdated` feeds it the boolean `StreamInfo.getOnline()` on every poll (roughly every 120 seconds) and prints through `MainGui.printLineByOwnerChannel`, which already routes info messages into the chat log. A new `printStreamLive` boolean setting gates only the printing, never the state tracking.

**Tech Stack:** Java 21, Gradle, Swing, JUnit 4 (`junit:junit:4.13.2`, `build.gradle:71`).

**Spec:** `docs/superpowers/specs/2026-08-28-stream-live-chat-lines-design.md`

## Global Constraints

- No em-dashes anywhere: code, comments, commit messages, docs. Use a comma, colon, semicolon, or parentheses.
- US English spelling everywhere (`behavior`, `color`, `honored`).
- Comments explain the why, never the what. Doc comments state the contract only, no implementation details.
- Booleans are named positively (`isOnline`, never `isNotOffline`).
- Match existing file style. Chatty source uses 4-space indent and `String+concatenation` in most places.
- Do NOT add an `@author` tag to new files. Existing files carry `@author tduva` (upstream author); new files in this fork should carry none.
- Never `git push`. Commit only.
- Before any commit, scan the diff for names, emails, API keys, and hardcoded paths like `/Users/<name>/`.

## File Structure

**Create:**
- `src/chatty/util/api/StreamLiveTracker.java` — the entire transition rule. No dependency on `StreamInfo`, `Settings`, or any GUI class, which is what makes it unit-testable. Roughly 45 lines.
- `test/chatty/util/api/StreamLiveTrackerTest.java` — unit tests for the above.

**Modify:**
- `src/chatty/SettingsManager.java:634` — register the `printStreamLive` setting.
- `src/chatty/lang/Strings.properties:1029` — checkbox label and tooltip.
- `src/chatty/gui/components/settings/MessageSettings.java:110-112` — the checkbox itself.
- `src/chatty/TwitchClient.java` — tracker field (near line 134), import (near line 30), print call and helper inside `MyStreamInfoListener` (near line 2896), `forget` call in `closeChannelStuff` (near line 682).
- `docs/DECISIONS.md` — append an entry.

**Task order matters:** Task 2 must land before Task 3. Task 3 calls `settings.getBoolean("printStreamLive")`, which requires the setting to have been registered first.

---

### Task 1: StreamLiveTracker

**Files:**
- Create: `src/chatty/util/api/StreamLiveTracker.java`
- Test: `test/chatty/util/api/StreamLiveTrackerTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `public class chatty.util.api.StreamLiveTracker`
  - `public enum StreamLiveTracker.Transition { NONE, WENT_LIVE, WENT_OFFLINE }`
  - `public Transition update(String stream, boolean isOnline)`
  - `public void forget(String stream)`

- [ ] **Step 1: Write the failing test**

Create `test/chatty/util/api/StreamLiveTrackerTest.java`:

```java
package chatty.util.api;

import chatty.util.api.StreamLiveTracker.Transition;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StreamLiveTrackerTest {

    @Test
    public void test_update_firstObservationOnline_returnsNone() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        assertEquals(Transition.NONE, tracker.update("teststream", true));
    }

    @Test
    public void test_update_firstObservationOffline_returnsNone() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        assertEquals(Transition.NONE, tracker.update("teststream", false));
    }

    @Test
    public void test_update_offlineToOnline_returnsWentLive() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        tracker.update("teststream", false);
        assertEquals(Transition.WENT_LIVE, tracker.update("teststream", true));
    }

    @Test
    public void test_update_onlineToOffline_returnsWentOffline() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        tracker.update("teststream", true);
        assertEquals(Transition.WENT_OFFLINE, tracker.update("teststream", false));
    }

    @Test
    public void test_update_repeatedSameState_returnsNone() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        tracker.update("teststream", true);
        assertEquals(Transition.NONE, tracker.update("teststream", true));
        assertEquals(Transition.NONE, tracker.update("teststream", true));
    }

    @Test
    public void test_update_afterForget_returnsNone() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        tracker.update("teststream", true);
        tracker.forget("teststream");
        assertEquals(Transition.NONE, tracker.update("teststream", false));
    }

    @Test
    public void test_update_separateStreams_trackedIndependently() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        tracker.update("teststream", false);
        tracker.update("otherstream", true);
        assertEquals(Transition.WENT_LIVE, tracker.update("teststream", true));
        assertEquals(Transition.WENT_OFFLINE, tracker.update("otherstream", false));
    }

    @Test
    public void test_forget_nullStream_doesNotThrow() {
        StreamLiveTracker tracker = new StreamLiveTracker();
        tracker.forget(null);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "chatty.util.api.StreamLiveTrackerTest"
```

Expected: compilation failure, `cannot find symbol: class StreamLiveTracker`.

- [ ] **Step 3: Write minimal implementation**

Create `src/chatty/util/api/StreamLiveTracker.java`:

```java
package chatty.util.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last known online state of streams and reports when it changes.
 *
 * The first observation of a stream only records the state, so joining a
 * channel that is already live does not look like the stream just going live.
 */
public class StreamLiveTracker {

    public enum Transition {
        NONE, WENT_LIVE, WENT_OFFLINE
    }

    /**
     * Concurrent because state is recorded from the API thread while it can be
     * dropped from the thread closing a channel.
     */
    private final Map<String, Boolean> lastOnline = new ConcurrentHashMap<>();

    /**
     * Record the current online state of a stream and report whether it
     * changed compared to the previous call.
     *
     * @param stream The stream name, must not be null
     * @param isOnline Whether the stream is currently online
     * @return WENT_LIVE or WENT_OFFLINE if the state changed, otherwise NONE,
     * which includes the first observation of this stream
     */
    public Transition update(String stream, boolean isOnline) {
        Boolean previous = lastOnline.put(stream, isOnline);
        if (previous == null || previous == isOnline) {
            return Transition.NONE;
        }
        return isOnline ? Transition.WENT_LIVE : Transition.WENT_OFFLINE;
    }

    /**
     * Drop the recorded state for a stream, so the next update() for it counts
     * as a first observation again.
     *
     * @param stream The stream name, null is ignored
     */
    public void forget(String stream) {
        if (stream != null) {
            lastOnline.remove(stream);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew test --tests "chatty.util.api.StreamLiveTrackerTest"
```

Expected: BUILD SUCCESSFUL, 8 tests passing. Read the terminating line of the output before claiming it passed.

- [ ] **Step 5: Commit**

```bash
git add src/chatty/util/api/StreamLiveTracker.java test/chatty/util/api/StreamLiveTrackerTest.java
git commit -m "Add StreamLiveTracker for stream online state transitions"
```

---

### Task 2: printStreamLive setting

**Files:**
- Modify: `src/chatty/SettingsManager.java:634`
- Modify: `src/chatty/lang/Strings.properties:1029`
- Modify: `src/chatty/gui/components/settings/MessageSettings.java:110-112`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: a registered boolean setting named `printStreamLive`, default `true`, readable via `settings.getBoolean("printStreamLive")`. Task 3 depends on this existing.

- [ ] **Step 1: Register the setting**

In `src/chatty/SettingsManager.java`, the "Message Types" block currently reads:

```java
        // Message Types
        settings.addBoolean("showJoinsParts", false);
        settings.addBoolean("showModMessages", false);
        settings.addBoolean("twitchnotifyAsInfo", true);
        settings.addBoolean("printStreamStatus", true);
        settings.addBoolean("showModActions", true);
```

Add one line directly after the `printStreamStatus` line:

```java
        settings.addBoolean("printStreamLive", true);
```

- [ ] **Step 2: Add the label and tooltip**

In `src/chatty/lang/Strings.properties`, after these two existing lines:

```
settings.boolean.printStreamStatus = Show stream status (e.g. title/game) in chat
settings.boolean.printStreamStatus.tip = Show on join and when it changes
```

add:

```
settings.boolean.printStreamLive = Show when the stream goes live/offline in chat
settings.boolean.printStreamLive.tip = Only when the online status actually changes, unlike the stream status which also changes on title/category edits
```

- [ ] **Step 3: Add the checkbox**

In `src/chatty/gui/components/settings/MessageSettings.java`, the method currently ends with:

```java
        otherSettingsPanel.add(d.addSimpleBooleanSetting(
                "printStreamStatus"),
                SettingsDialog.makeGbc(0, 3, 4, 1, GridBagConstraints.WEST));
    }
```

Add a second checkbox on the next grid row, before the closing brace:

```java
        otherSettingsPanel.add(d.addSimpleBooleanSetting(
                "printStreamLive"),
                SettingsDialog.makeGbc(0, 4, 4, 1, GridBagConstraints.WEST));
```

- [ ] **Step 4: Verify it compiles and the setting is wired**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL.

Then confirm all three edits landed:

```bash
grep -rn "printStreamLive" src/chatty/SettingsManager.java src/chatty/lang/Strings.properties src/chatty/gui/components/settings/MessageSettings.java
```

Expected: 4 lines, one in `SettingsManager.java`, two in `Strings.properties`, one in `MessageSettings.java`.

- [ ] **Step 5: Commit**

```bash
git add src/chatty/SettingsManager.java src/chatty/lang/Strings.properties src/chatty/gui/components/settings/MessageSettings.java
git commit -m "Add printStreamLive setting for stream live/offline chat lines"
```

---

### Task 3: Print the line from TwitchClient

**Files:**
- Modify: `src/chatty/TwitchClient.java` (import near line 30, field near line 134, helper and call near line 2896, `forget` near line 682)
- Modify: `docs/DECISIONS.md`

**Interfaces:**
- Consumes: `StreamLiveTracker`, `StreamLiveTracker.Transition`, `update(String, boolean)`, `forget(String)` from Task 1. The `printStreamLive` setting from Task 2.
- Produces: the user-visible feature. Nothing later depends on it.

**Background the implementer needs:**
- `MyStreamInfoListener` is an anonymous-use inner class constructed inline at `TwitchClient.java:265` and never stored in a field, so the tracker cannot live inside it: `closeChannelStuff` would not be able to reach it. It goes on `TwitchClient` itself, next to `streamStatusWriter`.
- Java runs instance field initializers before the constructor body, so a field initialized at its declaration is already non-null when line 265 constructs the listener.
- `streamInfoUpdated` may still hold a lock from `StreamInfoManager`, which is why the tracker uses a `ConcurrentHashMap` and does no blocking work.
- `printLineByOwnerChannel` produces a normal info message. `MainGui.java:4081` writes those to the chat log via `client.chatLog.info(...)`, so no logging code is needed here.

- [ ] **Step 1: Add the import**

In `src/chatty/TwitchClient.java`, the imports currently include:

```java
import chatty.util.api.StreamInfo.StreamType;
import chatty.util.api.StreamInfo.ViewerStats;
```

Add, keeping alphabetical order within the block:

```java
import chatty.util.api.StreamLiveTracker.Transition;
```

`StreamLiveTracker` itself needs no import, it is covered by the existing `import chatty.util.api.*;` at line 28.

- [ ] **Step 2: Add the field**

In `src/chatty/TwitchClient.java`, after:

```java
    public final StreamStatusWriter streamStatusWriter;
```

add:

```java
    private final StreamLiveTracker streamLiveTracker = new StreamLiveTracker();
```

- [ ] **Step 3: Call the tracker and print**

In `MyStreamInfoListener.streamInfoUpdated`, the body currently reads:

```java
        @Override
        public void streamInfoUpdated(StreamInfo info) {
            g.updateState(true);
            g.updateChannelInfo(info);
            g.updateStreamLive(info);
            g.addStreamInfo(info);
            String channel = "#"+info.getStream();
            if (isChannelOpen(channel)) {
                // Log viewerstats if channel is still open and thus a log
                // is being written
                chatLog.viewerstats(channel, info.getViewerStats(false));
                if (info.getOnline() && info.isValid()) {
                    chatLog.viewercount(channel, info.getViewers());
                }
            }
```

Add one call as the last statement inside the `isChannelOpen(channel)` block, directly after the `chatLog.viewercount` block:

```java
                printStreamLiveChange(info, channel);
```

Then add this method to `MyStreamInfoListener`, directly after `streamInfoUpdated`:

```java
        /**
         * Print a line when the stream changed between online and offline.
         *
         * @param info The updated StreamInfo
         * @param channel The channel to print to, must be open
         */
        private void printStreamLiveChange(StreamInfo info, String channel) {
            if (!info.isValidEnough()) {
                /**
                 * Stale data must not be reported as the stream going offline.
                 * The tab indicator can treat it as offline because it shows
                 * state, but a line claiming the stream just ended would be
                 * wrong.
                 */
                return;
            }
            /**
             * Track before reading the setting, so toggling the setting can't
             * produce a transition on the next update that never happened.
             */
            Transition transition = streamLiveTracker.update(info.getStream(), info.getOnline());
            if (transition != Transition.NONE && settings.getBoolean("printStreamLive")) {
                g.printLineByOwnerChannel(channel, "** "+info.getDisplayName()
                        +" is now "+(transition == Transition.WENT_LIVE ? "LIVE" : "OFFLINE")+" **");
            }
        }
```

- [ ] **Step 4: Drop state when the channel closes**

In `closeChannelStuff`, the guarded block currently reads:

```java
    private void closeChannelStuff(Room room) {
        // Check if not on any associated channel anymore
        if (!c.onOwnerChannel(room.getOwnerChannel())) {
            frankerFaceZ.left(room.getOwnerChannel());
            eventSub.unlistenRaid(room.getStream());
```

Add as the last statement inside that `if` block, after the final `eventSub.unlisten...` line:

```java
            streamLiveTracker.forget(room.getStream());
```

`room.getStream()` is null for whisper and special rooms; `forget` ignores null.

- [ ] **Step 5: Compile and run the full test suite**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL, with the 8 `StreamLiveTrackerTest` tests and all pre-existing tests passing. Read the terminating line of the output. If the command produces no exit line, it is still running, not done.

- [ ] **Step 6: Verify at runtime**

```bash
./gradlew macBuild
```

Quit Chatty, then:

```bash
rm -rf ~/Applications/Chatty.app && cp -R build/jpackage-mac/Chatty.app ~/Applications/Chatty.app
```

Launch it and confirm:
1. Settings has the new "Show when the stream goes live/offline in chat" checkbox below the existing stream status one, and it is checked.
2. Joining a live channel prints no live line (state seeding only).
3. When a joined channel actually goes live or ends, the `** <Name> is now LIVE **` or `** <Name> is now OFFLINE **` line appears within roughly two minutes, and the same line is present in that channel's file under the configured log directory.

Point 3 depends on a real stream changing state. If none is available during the session, say so explicitly rather than reporting it as verified.

- [ ] **Step 7: Append the decision record**

Append to `docs/DECISIONS.md`:

```markdown
## 2026-08-28: Explicit stream live/offline lines in chat and chat log

**What:** New `StreamLiveTracker` (`chatty/util/api/`) records the last known online state per stream and reports `WENT_LIVE` / `WENT_OFFLINE` transitions. `TwitchClient.MyStreamInfoListener.streamInfoUpdated` feeds it `StreamInfo.getOnline()` for open channels and prints `** <Name> is now LIVE **` or `** <Name> is now OFFLINE **` via `printLineByOwnerChannel`, which routes into the chat log through the existing `chatLog.info` path. Gated by a new `printStreamLive` setting (default on).

**Why:** The existing `printStreamStatus` output fires on any change to `StreamInfo.getFullStatus()`, so a title or category edit while live prints the same `~text~` format as going live. Reading a log after the fact, a go-live was indistinguishable from a title change. The tab live indicator already had the right signal (the boolean `getOnline()`), but nothing wrote it anywhere durable.

**Tradeoffs:** Polling-based, so detection lags by up to the 120 second `UPDATE_STREAMINFO_DELAY`, and going offline lags further due to the deliberate `recheckOffline` delay in `StreamInfo`; the offline line is occasionally suppressed entirely when followed-stream data disagrees. Two deliberate differences from the tab indicator: the first observation after joining seeds state silently, so joining a live channel does not print a false go-live; and `!isValidEnough()` is skipped rather than treated as offline, since stale API data must not be reported as the stream ending. Scope is limited to joined channels, because followed streams the user is not in have no channel tab and no log file to write to.
```

- [ ] **Step 8: Commit**

```bash
git add src/chatty/TwitchClient.java docs/DECISIONS.md
git commit -m "Print stream live/offline lines in chat and chat log"
```

---

## Verification Summary

| Check | Command | Expected |
|---|---|---|
| Unit tests | `./gradlew test --tests "chatty.util.api.StreamLiveTrackerTest"` | 8 passing |
| Full suite | `./gradlew build` | BUILD SUCCESSFUL |
| Setting wired | `grep -rn "printStreamLive" src/` | 5 hits across 4 files |
| Runtime | manual, see Task 3 Step 6 | checkbox present, no line on join, line on real transition |
