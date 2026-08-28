# Stream live/offline lines in chat and chat log

Date: 2026-08-28
Status: Draft, awaiting approval

## Problem

Chatty already shows a live indicator on the channel tab, but nothing writes a
distinguishable go-live or went-offline event to chat or to the log files.

The existing `printStreamStatus` output is close but not usable as an event
signal. `TwitchClient.MyStreamInfoListener.streamInfoStatusChanged`
(`src/chatty/TwitchClient.java:2923`) fires whenever the string returned by
`StreamInfo.getFullStatus()` changes, and prints it as `~<text>~`. Going live
prints `~<title> (<game>)~`, going offline prints `~Stream offline~`, but a
plain title or category edit while live prints the identical format. Reading a
log after the fact, you cannot tell a go-live from a title change.

## Goal

Print an explicit, unambiguous line in the channel when a stream goes live or
goes offline, for channels the user has joined. The line reaches the on-disk
log automatically through the existing info-message logging path.

Polling is acceptable. No EventSub work is in scope.

## Current mechanism (reference)

The tab live indicator, which this feature mirrors:

1. `StreamInfoManager` refreshes stream info for open channels every
   `UPDATE_STREAMINFO_DELAY = 120` seconds
   (`src/chatty/util/api/StreamInfoManager.java:39`).
2. Each refresh fires `MyStreamInfoListener.streamInfoUpdated`
   (`src/chatty/TwitchClient.java:2889`).
3. That calls `g.updateStreamLive(info)`, which on the EDT calls
   `channels.setStreamLive(info.stream, info.isValidEnough() && info.getOnline())`
   (`src/chatty/gui/MainGui.java:4374`).
4. `Channels.setStreamLive` (`src/chatty/gui/Channels.java:692`) maintains a
   `Set<String> liveStreams` and pushes the flag to the tab.
5. `DockStyledTabContainer.setLive` (`src/chatty/gui/DockStyledTabContainer.java:108`)
   holds the edge detection (`if (isLive != this.isLive)`) and repaints the dot.

The key difference from `printStreamStatus`: this path keys off the boolean
`info.getOnline()`, not off string equality of the status text.

## Design

### Signal

Hook `MyStreamInfoListener.streamInfoUpdated`, inside the existing
`isChannelOpen(channel)` block, and read the boolean `info.getOnline()`.

Timing behavior is inherited from `StreamInfo` and is not changed:

- Detection lags by up to the ~120s poll interval.
- Going offline is deliberately delayed by the `recheckOffline` logic
  (`src/chatty/util/api/StreamInfo.java:314`).
- `setOffline()` can be suppressed entirely when followed-stream data
  disagrees (`src/chatty/util/api/StreamInfo.java:302`), so an offline line
  can occasionally be skipped.

### New component: StreamLiveTracker

New file `src/chatty/util/api/StreamLiveTracker.java`. Holds last-known online
state per stream and reports transitions.

```java
public enum Transition { NONE, WENT_LIVE, WENT_OFFLINE }

public Transition update(String stream, boolean isOnline)
public void forget(String stream)
```

Contract:

- The first `update` for a stream records the state and returns `NONE`.
- A subsequent `update` with the same state returns `NONE`.
- A change from offline to online returns `WENT_LIVE`; the reverse returns
  `WENT_OFFLINE`.
- `forget` drops the stored state, so the next `update` seeds silently again.
- `forget(null)` is a no-op. `closeChannelStuff` runs for whisper and special
  rooms too, and `Room.getStream()` returns null when no stream is associated
  (`src/chatty/Room.java:123`), so the tracker absorbs that rather than
  pushing a null check to the call site.

It exists as its own class because the transition rule is the only part of
this feature worth testing, and the inner listener it would otherwise live in
is not testable.

Dependencies: none beyond a `Map`. No `StreamInfo`, no `Settings`, no GUI.

### Wiring

In `streamInfoUpdated`, alongside the existing `chatLog.viewerstats` calls:

```java
if (info.isValidEnough()) {
    Transition t = liveTracker.update(info.getStream(), info.getOnline());
    if (t != Transition.NONE && settings.getBoolean("printStreamLive")) {
        g.printLineByOwnerChannel(channel, t == Transition.WENT_LIVE
                ? "** " + info.getDisplayName() + " is now LIVE **"
                : "** " + info.getDisplayName() + " is now OFFLINE **");
    }
}
```

`liveTracker.forget(room.getStream())` is called from `closeChannelStuff`
(`src/chatty/TwitchClient.java:672`), inside the existing
`!c.onOwnerChannel(room.getOwnerChannel())` guard alongside the
`eventSub.unlisten*` calls, so state is dropped only once the user is off
every associated channel. Rejoining then re-seeds rather than replaying a
stale transition.

The tracker is updated before the setting is read. Toggling the setting
mid-session therefore cannot manufacture a spurious event on the next poll.

### Two deliberate differences from the tab indicator

1. The first observation after joining prints nothing, it only seeds state.
   Without this, every join of a live channel would emit a false
   "is now LIVE". A dot showing steady state is correct; an event line
   claiming a transition that did not happen is not.

2. `!isValidEnough()` is skipped rather than treated as offline. The tab
   computes `isValidEnough() && getOnline()`, so stale data darkens the dot.
   Applying that to an event line would print "is now OFFLINE" because API
   data went stale, which would be false.

### Output format

`** <DisplayName> is now LIVE **` and `** <DisplayName> is now OFFLINE **`.

`**` delimiters match the existing Chatty info style, for example the
not-found message at `src/chatty/TwitchClient.java:2905`. `~` is deliberately
avoided so these lines never collide with `printStreamStatus` output.

`info.getDisplayName()` returns the correctly capitalized name where Twitch
provides one, and the lowercase login otherwise.

### Setting

`printStreamLive`, boolean, default `true`.

- Registered next to `printStreamStatus` in `SettingsManager`
  (`src/chatty/SettingsManager.java:634`).
- Checkbox added below the existing one in `MessageSettings`
  (`src/chatty/gui/components/settings/MessageSettings.java:110`).
- Label and tooltip added to `src/chatty/lang/Strings.properties`, next to
  `settings.boolean.printStreamStatus` at line 1028.

Kept separate from `printStreamStatus` so the two can be controlled
independently. On a go-live both would otherwise fire at the same moment.

### Reaching the log file

No new logging code. `printLineByOwnerChannel` produces a normal info message,
which `MainGui` writes via `client.chatLog.info(...)`
(`src/chatty/gui/MainGui.java:4081`). `ChatLog.info` is gated by `logInfo`
(default `true`) and `isChanEnabled`, which reads `logMode` (default
`always`), at `src/chatty/util/chatlog/ChatLog.java:158`. With file logging
configured, these lines land in the channel's log alongside the existing
status lines.

## Scope boundaries

In scope: channels the user has joined.

Out of scope: followed streams the user is not in. Chatty polls those every
200 seconds for the Live Streams list, but there is no channel tab and no
channel log file to write to, so it would need a separate destination. Not
part of this change.

## Testing

New `test/chatty/util/api/StreamLiveTrackerTest.java`, testing
`StreamLiveTracker` directly:

- `test_update_firstObservationOnline_returnsNone`
- `test_update_firstObservationOffline_returnsNone`
- `test_update_offlineToOnline_returnsWentLive`
- `test_update_onlineToOffline_returnsWentOffline`
- `test_update_repeatedSameState_returnsNone`
- `test_update_afterForget_returnsNone`
- `test_update_separateStreams_trackedIndependently`

Run with `./gradlew test --tests "chatty.util.api.StreamLiveTrackerTest"`.

Manual verification: join a channel that is about to go live or end, confirm
one line appears in the channel and the same line appears in the channel's
file under the configured log directory, and confirm no line appears on join.

## Files changed

New:

- `src/chatty/util/api/StreamLiveTracker.java`
- `test/chatty/util/api/StreamLiveTrackerTest.java`

Modified:

- `src/chatty/TwitchClient.java` (tracker field, call in `streamInfoUpdated`,
  `forget` in `closeChannelStuff`)
- `src/chatty/SettingsManager.java` (register `printStreamLive`)
- `src/chatty/gui/components/settings/MessageSettings.java` (checkbox)
- `src/chatty/lang/Strings.properties` (label, tooltip)
- `docs/DECISIONS.md` (entry for this change)

## Open items

Both are naming choices, cheap to change before implementation:

- Setting name `printStreamLive`.
- Line wording `** <Name> is now LIVE **`.
