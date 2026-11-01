# Live stream chat leaderboard

Date: 2026-11-01
Status: Draft, awaiting approval

## Problem

During a live stream the user wants to know who has sent the most chat
messages so far in that stream, and to share it. Today that means running the
external `top-chatters --last-session` script against Chatty's log files,
copying the output, and pasting it into Discord. It can't be pasted into Twitch
chat because a Twitch chat message can't contain line breaks.

## Goal

A window in Chatty that shows a live-updating ranking of chatters by message
count for the current stream of a channel. It has a button that posts the top
3 to that channel's chat as three separate messages.

Success: with a stream live, the user opens the window from a menu and sees
counts that match what the external script would report for the same stream
(see "Differences from the script"). The counts keep climbing while chat moves.
A Chatty restart or reconnect partway through the stream doesn't lose or
double-count anything, and one click posts three lines that all reach chat.

## Non-goals

- Posting on a timer, or posting anything the user didn't click to post. The
  user was explicit: never post automatically.
- Other leaderboards (bits, gift subs, stream snapshot). Those are likely
  follow-ups, but they're out of scope here. Don't build a tab or plugin
  framework for them yet.
- Past streams, date ranges, or exports to Discord or files.
- Counting from anything except Chatty's own log files.

## Anti-goals

- Counts that reset or jump when Chatty restarts or reconnects. The user
  specifically rejected "count since Chatty started".
- Lines 2 and 3 of a post being silently dropped by Twitch.
- Spamming chat: exactly 3 lines per click, no header line.

## Current mechanism (reference)

- **Log writing.** `ChatLog.message` (`src/chatty/util/chatlog/ChatLog.java:130`)
  renders `logMessageTemplate` (default
  `$if(timestamp,$(timestamp) )<$(full-nick2)>$if(action,*) $(msg)`,
  `src/chatty/SettingsManager.java:735`) and queues it to the `LogManager`
  writer thread. `LogFile.write` flushes after every line
  (`src/chatty/util/chatlog/LogFile.java:119`), so a line is readable on disk
  shortly after the message arrives.
- **Log file naming.** `LogWriter.getFile`/`addFile`
  (`src/chatty/util/chatlog/LogWriter.java:105`) choose the file from settings:
  `logSplit` (`never` by default; `daily`, `weekly` or `monthly` add a
  `yyyy-MM-dd_` prefix), `logSubdirectories` (one folder per channel), and a
  `-N` suffix when the plain name is locked by another instance. The base
  path comes from `ChatLog.getPath()`.
- **Timestamps.** `logTimestamp` defaults to `[yyyy-MM-dd HH:mm:ss]` in local
  time (`SettingsManager.java:754`). It can be set to `off`, or to a format
  with no date.
- **Out-of-order lines.** `ChatLog.outageBackfillMessage` writes messages
  recovered after a reconnect with their original send time. These land in
  the file after newer lines.
- **Bots.** `full-nick2` includes the `^` sigil for users Chatty considers
  bots (`User.isBot()`, fed by the `botNames` and BTTV/FFZ bot settings).
- **Stream start.** `StreamInfo.getTimeStartedWithPicnic()`
  (`src/chatty/util/api/StreamInfo.java:623`) gives the start time in epoch ms,
  treating short drops (up to 10 minutes) as the same stream.
  `getTimeStarted()` resets on every drop.
- **Slow mode and roles.** `ChannelState.slowMode()`
  (`src/chatty/ChannelState.java:132`) gives the room's slow mode in seconds.
  `User.hasModeratorRights()` and the VIP/broadcaster checks on the local
  user give the user's role in the room.
- **Sending.** `TwitchClient.textInput(Room, String, Parameters)`
  (`src/chatty/TwitchClient.java:931`) is the path a typed message takes.
- **Menu.** The Extra menu is created in `MainMenu`
  (`src/chatty/gui/MainMenu.java:79`), and its items are added at
  `MainMenu.java:166-196`.
- **Names in the log.** `full-nick2` is the mode sigils plus
  `User.getCustomNick()` (`src/chatty/User.java:1223`). For a user with a
  localized display name it is `Localized (login)` (`Parameters.java:241`).
  If the Chatty user set a custom nick for someone, only that nick is logged.
- **Sending is optimistic.** A sent line is printed and logged before Twitch
  accepts it (`TwitchClient.java:988-990`). A rejected send shows the fork's
  per-message error with a Resend link.

## Design

### Entry point

A "Stream Leaderboard" item in the Extra menu opens a non-modal window for
the channel of the active tab. *(Builder's call: whether the window follows
the active tab or stays on the channel it was opened for. Either is fine, but
it must show which channel it's for.)*

### What the window shows

- The channel name, and either "Live since <local time>, <total> messages"
  or "Ended <local time> (<start>-<end>), <total> messages" for the last
  stream.
- The full ranking, scrollable: rank, display name, message count.
- A "Post top 3" button.
- A status line for the cases where counts can't be produced (below).

### Stream boundary

The boundary comes from two sources that already exist, so no new saved
state is needed (the user's suggestion):

- **Twitch:** `getTimeStartedWithPicnic()`, which treats drops of up to 10
  minutes as one stream but only remembers that while Chatty is running.
- **Log markers:** `** <name> is now LIVE **` and `** <name> is now OFFLINE **`
  info lines (`TwitchClient.java:2975`). These are written only when Chatty
  sees a transition while running (`StreamLiveTracker`: the first
  observation after a start or join writes nothing). They lag the real
  change by up to the ~120 s stream-info poll, and they require the
  `printStreamLive` and `logInfo` settings (both on by default).

**Short-drop joining.** A LIVE marker that comes within 10 minutes of the
previous OFFLINE marker continues the same stream. This mirrors the picnic
rule. The window walks back through the markers this way to find where the
stream began. *(My call.)*

**Channel live** (`getOnline()` and `isValidEnough()` true, the same check
Chatty uses for its own LIVE/OFFLINE lines, so a briefly stale stream info
does not flip the board to ended):

- Start at Twitch's picnic start.
- Take the latest OFFLINE marker from 10 minutes before that start to 3
  minutes after it (the marker can trail the restart by one stream-info
  poll). If the marker directly before that OFFLINE is a LIVE marker, the
  start moves back to that LIVE marker. The rule then repeats from the new
  start, considering only OFFLINE markers before that LIVE marker, so it
  never reuses a marker it already passed. If that marker is anything else,
  such as the OFFLINE of an earlier stream, the walk stops. This covers a
  Chatty restart after the streamer had a short drop. Known gap: if Chatty
  restarted between two short drops (two OFFLINE markers in a row in the
  window), the walk stops and the first segment is not counted.
- The stream ends at "now".
- If Twitch's start is `-1` (no stream data yet), show "Stream start
  unknown".

**Channel not live:** show the most recent stream, labeled as ended with its
start and end times. This also covers a short drop, which shows the board as
ended instead of blank until the stream returns.

- If this Chatty session saw the stream, use Twitch's picnic start (it isn't
  reset on going offline). The end is the earliest OFFLINE marker from the
  last-online time to 10 minutes after it, which includes the final chat
  before the offline poll and matches the log-only end after a restart; with
  no such marker, the last-online time is the end. The last-online time
  drifts by a few milliseconds between reads, so two ended ranges with the
  same start whose ends differ by under 5 seconds count as the same range and
  do not trigger a rebuild.
- Otherwise use the log: the last OFFLINE marker is the end, the LIVE marker
  directly before it is the start, and the start is then walked back with
  short-drop joining. If the marker before that OFFLINE is not a LIVE marker,
  there is no bounded stream.
- If the log has no LIVE/OFFLINE pair to bound a stream (for example, Chatty
  was closed when it ended), show "No recent stream found in the logs".

The epoch-ms Twitch times are converted to local time and compared with the
local-time log timestamps. The board covers every chat line whose timestamp
falls between the start and the end.

### Counting from the logs

The log files are the only source of counts. Nothing is counted from
in-memory chat events, so there's nothing to reconcile or double-count.

1. **Initial scan when the window opens.** Find every log file for the
   channel that could hold lines from the stream start onward. With `daily`,
   `weekly` or `monthly` split, that's the files whose date prefix covers
   start-day through today. With `never`, it's the single file. Read each
   file in full. Ignore `-N` suffix files (written when another instance held
   the lock). They often duplicate lines from the plain file, and the
   external script ignores them too. *(My call.)*
2. **Incremental reads.** Every few seconds while the window is open
   *(builder's call on the interval; around 3 to 5 seconds)*, read only the
   bytes appended since the last read of each file. Also pick up a file that
   didn't exist before (the midnight split). Don't re-read whole files on a
   timer: with `logSplit=never` a file can hold years of chat.
3. **Line filtering.** Check every line's own timestamp against the stream
   start. Never stop at the first too-old line, because backfilled lines are
   out of order.
4. **Parsing.** A chat line is `<timestamp> <sigils+name>[*] message`.
   Strip the leading sigils from the name. When the name has the
   `Localized (login)` form, group by the login. Otherwise group by the
   lowercase name. Show and post the name as most recently logged. A custom
   nick the user set for someone is grouped under that nick; that's an
   accepted limitation. Lines starting with `#`, info lines and system lines
   are not chat and are ignored. *(My call.)*
5. **`/me` messages** (`<name>* text`) count as messages. *(My call. The
   external script's regex skips them, so this is a deliberate difference.)*
6. **Bots** are excluded when the name carries the `^` sigil, or when it's
   one of `streamelements`, `nightbot`, `moobot`, `streamlabs`, `fossabot` or
   `sery_bot`. This matches the external script's rule. The user's own
   messages count, including posted leaderboard lines. *(My call.)*
7. **Ties** are ordered by who sent their first message in the stream
   earlier. *(My call.)*

Parsing and counting do file I/O, so they stay off the EDT. Results are
handed to the window on the EDT.

### When counts can't be produced

Show a plain status line instead of a wrong or empty board when:

- logging is off, or this channel is excluded from logging (`logMode`
  settings; `ChatLog.isChanEnabled` is private and needs an accessor);
- `logTimestamp` is `off` or has no date, so lines can't be placed in time;
- `logMessageTemplate` isn't the default, so lines can't be parsed reliably.
  *(My call. Supporting custom templates is out of scope.)*

### Posting the top 3

- The button takes a snapshot of the current top 3 and sends three separate
  messages to the channel, one per rank:
  ```
  #1 viewer_one: 342 messages
  #2 viewer_two: 289 messages
  #3 viewer_three: 201 messages
  ```
  With fewer than 3 ranked chatters, it sends only the lines that exist. Use
  "message" for a count of 1. *(My call.)*
- Messages go through the same path as typed input (`textInput`), so they're
  logged, echoed, and handled by the fork's existing echo-dedup logic.
- **Pacing.** If the local user is broadcaster, mod or VIP in the room, the
  lines are sent back to back. Otherwise Chatty waits between lines: the
  room's slow mode in seconds plus 1 second, and at least about 1.5 seconds
  when slow mode is off. *(The user asked for a short delay when not
  mod/VIP. The exact numbers are my call.)*
- The button is disabled while a post is in progress, so a double click
  can't send six lines. If the channel is left mid-post, the remaining lines
  are dropped.
- The poster doesn't track whether Twitch accepted each line. A rejected
  line shows the existing per-message error and Resend link in chat.
  *(My call.)*

### Differences from the external script (intended)

- `/me` messages count here and don't in the script.
- The stream boundary comes from Twitch's start time plus LIVE/OFFLINE
  markers, not from activity gaps.

## Testing

Unit tests for the log parsing and counting, using fixture log text written
to temp directories (never `~/.chatty/logs`). Cover:

- lines before the start excluded and after included;
- an out-of-order backfilled line;
- `/me` lines;
- bot sigil and the bot-name list;
- sigil stripping and case-insensitive grouping;
- incremental reads that pick up appended bytes only;
- a stream that crosses the daily split;
- the not-parseable settings states;
- boundary resolution: live with no prior markers, a restart after a short
  drop (OFFLINE then LIVE within 10 minutes extends the start back), a gap
  over 10 minutes (doesn't extend), the last ended stream from markers only,
  and no bounding pair.

Unit tests for post pacing (mod vs non-mod, slow mode on and off) against
a fake sender. A manual check in the running app against a live channel,
comparing the result with `chatty_query.py top-chatters --last-session`.

## Left to the builder

- Class and package names, window layout, and whether the window follows the
  active tab.
- The incremental read interval.
- How the reader is structured and wired to `ChatLog`'s path and settings.
