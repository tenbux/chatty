# Stream Chat Leaderboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Extra-menu window that shows a live-updating per-stream chatter ranking read from Chatty's own log files, with a button that posts the top 3 to chat as three separate messages.

**Architecture:** Pure, unit-tested pieces in `chatty.util.chatlog`. A line parser, a stream-range resolver (Twitch start time plus LIVE/OFFLINE log markers), a tally, an incremental per-channel log reader, and post formatting/pacing feed one `StreamLeaderboard` controller that runs off the EDT. A Swing dialog polls the controller and renders snapshots on the EDT. Posting goes through `TwitchClient.textInput`.

**Tech Stack:** Java 21, Swing, JUnit 4.13.2 (`org.junit.Test`, `org.junit.Assert.*`), Gradle (`./gradlew test`).

**Spec:** `docs/superpowers/specs/2026-11-01-stream-chat-leaderboard-design.md`

## Global Constraints

- Never post to chat except on a "Post top 3" click. No timer posting.
- Post exactly the top 3 (fewer if fewer ranked), no header line, format `#<rank> <name>: <count> messages`, with `message` when the count is 1.
- Short-drop join gap: 10 minutes (`10 * 60 * 1000` ms), matching `StreamInfo` picnic.
- Marker lookback when resolving from logs: 7 days. *(Plan's call: the spec gives no number.)*
- Bots: name has the `^` sigil, or the lowercase name is one of `streamelements`, `nightbot`, `moobot`, `streamlabs`, `fossabot`, `sery_bot`.
- Sigil characters stripped from the front of names: `~&@%$+!^`.
- `/me` lines (`<name>* text`) count.
- `-N.log` suffix files are ignored.
- Supported only with `logMessageTemplate` equal to its default and `logTimestamp` equal to `[yyyy-MM-dd HH:mm:ss]`. Otherwise show a status line instead of a board.
- Pacing: privileged (broadcaster, mod or VIP) is 0 ms between lines. Otherwise `slowMode*1000 + 1000` ms if slow mode > 0, else `1500` ms.
- Log timestamps are local time: convert with `ZoneId.systemDefault()`, second precision.
- No file I/O on the EDT.
- Status copy: `Stream start unknown`, `No recent stream found in the logs`, `Logging is off for this channel`, `Log format not supported (custom timestamp or message template)`.
- Tests use temp directories only, never `~/.chatty/logs`, and placeholder usernames only.
- No em dashes in code, comments, or UI text. US English.

## Review Focus

1. **A log line half-written at the moment of a read** (no trailing newline yet). Expect it to be counted once, after it completes, not dropped and not double-counted. The test is in Task 4.
2. **The stream crossing midnight with daily split.** Expect the new day's file to be picked up by the incremental read without reopening the window. The test is in Task 4.
3. **The stream going live while the window shows an ended stream.** Expect the board to switch to the new stream with a fresh count. The test is in Task 5.
4. **A backfilled line older than the stream start, appended late.** Expect it to be excluded. A backfilled line inside the range is included. The test is in Task 5.
5. **A double click on Post, or leaving the channel mid-post.** Expect at most 3 lines, and none sent after the channel closes. This is a manual check in Task 6.

---

### Task 1: Log line parser

**Files:**
- Create: `src/chatty/util/chatlog/LeaderboardLogLine.java`
- Test: `test/chatty/util/chatlog/LeaderboardLogLineTest.java`

**Interfaces:**
- Produces:
  - `sealed interface LeaderboardLogLine permits ChatLine, StatusMarker`
  - `record ChatLine(long timeMs, String key, String name, boolean isBot)`: `key` is the grouping key, `name` is the display form without sigils.
  - `record StatusMarker(long timeMs, boolean isLive)`
  - `static LeaderboardLogLine parse(String line, ZoneId zone)`: returns `null` for anything else (headers `#`, info, system, `-` separators, malformed).

- [ ] **Step 1: Write the failing tests** (zone `ZoneId.of("UTC")`; `ms("2026-10-31 20:00:05")` is a helper using that zone):
  - `test_parse_plainMessage_returnsChatLine`: `[2026-10-31 20:00:05] <@Viewer_One> hi` gives `ChatLine(ms(...), "viewer_one", "Viewer_One", false)`.
  - `test_parse_meAction_returnsChatLine`: `<Viewer_One>* waves` gives a `ChatLine` with key `viewer_one`.
  - `test_parse_botSigil_isBot`: `<%^SomeBot> x` has `isBot` true.
  - `test_parse_knownBotName_isBot`: `<StreamElements> x` has `isBot` true.
  - `test_parse_localizedName_groupsByLogin`: `<+名前 (viewer_two)> x` gives key `viewer_two` and name `名前 (viewer_two)`.
  - `test_parse_liveMarker_returnsStatusMarker`: `[2026-10-31 19:58:00] ** Some Streamer is now LIVE **` gives `StatusMarker(ms, true)`. The OFFLINE form gives `false`.
  - `test_parse_nonChat_returnsNull`: `# Log started: ...`, `-`, `[..] You have joined #x`, `[..] ~Title (Game)~`, and an empty string all give `null`.

- [ ] **Step 2: Run** `./gradlew test --tests "chatty.util.chatlog.LeaderboardLogLineTest"`. Expected: compile FAIL (class missing).

- [ ] **Step 3: Implement** `LeaderboardLogLine.parse` with two precompiled patterns:
  - chat: `^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\] <([^>]+)>\*? (.*)$`
  - marker: `^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\] \*\* .+ is now (LIVE|OFFLINE) \*\*$`

  Test the marker pattern first, because a marker line never starts with `<`.

  Key: strip leading sigils. If the rest ends in ` (login)`, the key is `login` lowercased. Otherwise the key is the rest lowercased.

- [ ] **Step 4: Run** the same command. Expected: PASS.

- [ ] **Step 5: Commit** `Add log line parser for the stream leaderboard`.

### Task 2: Stream range resolver

**Files:**
- Create: `src/chatty/util/chatlog/StreamRangeResolver.java`
- Test: `test/chatty/util/chatlog/StreamRangeResolverTest.java`

**Interfaces:**
- Consumes: `StatusMarker` (Task 1).
- Produces:
  - `record TwitchStreamState(boolean isLive, long picnicStartMs, long lastOnlineMs)`: use `-1` for unknown.
  - `sealed interface Resolution permits StreamRange, Unavailable`
  - `record StreamRange(long startMs, long endMs, boolean isLive)`: `endMs == Long.MAX_VALUE` when live.
  - `record Unavailable(String reason)`
  - `static Resolution resolve(List<StatusMarker> markers, TwitchStreamState twitch)`: `markers` is in any order, and the method sorts a copy by time.

Algorithm (from the spec, `JOIN_GAP = 10 min`):

```
extendBack(start):
  loop:
    o = latest OFFLINE marker with |o.time - start| <= JOIN_GAP
    l = latest LIVE marker with l.time < o.time
    if o or l missing, or l.time >= start: return start
    start = l.time

live:            picnicStart == -1 ? Unavailable("Stream start unknown")
                                   : StreamRange(extendBack(picnicStart), MAX, true)
offline+session: picnicStart != -1 and lastOnline != -1
                 -> StreamRange(extendBack(picnicStart), lastOnline, false)
offline+logs:    the most recent marker must be OFFLINE (e), with a LIVE (l) before it
                 -> StreamRange(extendBack(l.time), e.time, false)
                 else Unavailable("No recent stream found in the logs")
```

`OFFLINE` can land just after the Twitch start, because offline detection lags. That's why the check uses `|o.time - start|`.

- [ ] **Step 1: Write the failing tests** (times in minutes from an epoch base, via a helper):
  - `test_resolve_liveNoMarkers_usesTwitchStart`: live, picnic 100, no markers. Expect `StreamRange(100, MAX, true)`.
  - `test_resolve_liveUnknownStart_unavailable`: live, picnic -1. Expect `Unavailable("Stream start unknown")`.
  - `test_resolve_liveAfterShortDrop_extendsToEarlierLive`: markers LIVE@10, OFFLINE@95; live, picnic 100. Expect start 10.
  - `test_resolve_liveAfterLongGap_doesNotExtend`: LIVE@10, OFFLINE@80; picnic 100. Expect start 100.
  - `test_resolve_chainOfShortDrops_extendsAcrossAll`: LIVE@10, OFFLINE@50, LIVE@55, OFFLINE@95; picnic 100. Expect start 10.
  - `test_resolve_offlineWithSession_usesSessionTimes`: not live, picnic 100, lastOnline 300. Expect `StreamRange(100, 300, false)`.
  - `test_resolve_offlineFromLogs_lastPair`: not live, picnic -1; LIVE@10, OFFLINE@200. Expect `StreamRange(10, 200, false)`.
  - `test_resolve_offlineLastMarkerLive_unavailable`: LIVE@10, OFFLINE@200, LIVE@500 (no OFFLINE after it). Expect `Unavailable("No recent stream found in the logs")`.
  - `test_resolve_offlineNoMarkers_unavailable`.

- [ ] **Step 2: Run** `./gradlew test --tests "chatty.util.chatlog.StreamRangeResolverTest"`. Expected: compile FAIL.

- [ ] **Step 3: Implement** as above.

- [ ] **Step 4: Run** it again. Expected: PASS.

- [ ] **Step 5: Commit** `Resolve stream boundaries from Twitch start time and log markers`.

### Task 3: Chatter tally

**Files:**
- Create: `src/chatty/util/chatlog/ChatterTally.java`
- Test: `test/chatty/util/chatlog/ChatterTallyTest.java`

**Interfaces:**
- Consumes: `ChatLine` (Task 1).
- Produces: `void add(ChatLine line)` (ignores bots), `List<Entry> ranking()`, `int total()`, and `record Entry(String name, int count)`.

Ranking is by count descending. Ties go to the earlier first-message time, which may differ from insertion order because of backfill. The shown name is the `name` of the line with the latest `timeMs` for that key.

- [ ] **Step 1: Write the failing tests:**
  - `test_ranking_ordersByCountDesc`
  - `test_ranking_tieGoesToEarlierFirstMessage`: add B@t2 and then A@t1 (out of order), one message each. Expect A first.
  - `test_add_bot_excludedFromRankingAndTotal`
  - `test_ranking_usesLatestCapitalization`: `viewer_one`@t1 then `Viewer_One`@t2. Expect name `Viewer_One` and count 2.

- [ ] **Step 2: Run** `./gradlew test --tests "chatty.util.chatlog.ChatterTallyTest"`. Expected: compile FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run.** Expected: PASS.

- [ ] **Step 5: Commit** `Add chatter tally for the stream leaderboard`.

### Task 4: Incremental channel log reader

**Files:**
- Create: `src/chatty/util/chatlog/ChannelLogReader.java`
- Test: `test/chatty/util/chatlog/ChannelLogReaderTest.java`

**Interfaces:**
- Produces:
  - `ChannelLogReader(Path logDir, String channel, String logSplit)`: `logDir` already includes the channel subdirectory when `logSubdirectories` is on (the caller resolves it). `channel` is `#name`, and `logSplit` is the setting value.
  - `void scanFrom(long fromMs, Consumer<String> lines) throws IOException`: resets all offsets, then reads every complete line of each file that may hold lines at or after `fromMs` and records its end offset.
  - `void readAppended(Consumer<String> lines) throws IOException`: reads only complete lines added since the last scan or read, including files that appeared since (the midnight split).

File selection:
- `logSplit` `never`: just `<channel>.log`.
- Otherwise, files named `yyyy-MM-dd_<channel>.log`. A file with prefix P covers `[P, next file's P)`; include it if that interval reaches `fromMs`. This works for daily, weekly and monthly without knowing the period.
- Ignore `-N.log`.
- Read as UTF-8. Keep the bytes of an incomplete final line, and don't advance the offset past them.

- [ ] **Step 1: Write the failing tests** (`TemporaryFolder` rule, channel `#testchan`, `daily` unless noted):
  - `test_scanFrom_skipsFilesEndingBeforeFrom`: files for 10-29, 10-30 and 10-31, with `fromMs` on 10-30 12:00. Only the 10-30 and 10-31 lines are emitted.
  - `test_scanFrom_ignoresSuffixFiles`: `2026-10-31_#testchan-1.log` is never read.
  - `test_scanFrom_neverSplit_readsSingleFile`
  - `test_readAppended_returnsOnlyNewLines`: scan, append 2 lines, and `readAppended` emits exactly those 2.
  - `test_readAppended_partialLineEmittedOnceCompleted`: append `"[..] <a> hel"` with no newline; nothing is emitted. Append `"lo\n"`; exactly one line `"[..] <a> hello"` is emitted.
  - `test_readAppended_picksUpNewDayFile`: after a scan, create `2026-11-01_#testchan.log`; its lines are emitted.

- [ ] **Step 2: Run** `./gradlew test --tests "chatty.util.chatlog.ChannelLogReaderTest"`. Expected: compile FAIL.

- [ ] **Step 3: Implement** with `FileChannel` positioned reads, `Map<Path, Long>` offsets, and `Map<Path, byte[]>` pending partial bytes.

- [ ] **Step 4: Run.** Expected: PASS.

- [ ] **Step 5: Commit** `Add incremental per-channel log reader`.

### Task 5: Leaderboard controller and post formatting

**Files:**
- Create: `src/chatty/util/chatlog/StreamLeaderboard.java`
- Create: `src/chatty/util/chatlog/LeaderboardPost.java`
- Test: `test/chatty/util/chatlog/StreamLeaderboardTest.java`
- Test: `test/chatty/util/chatlog/LeaderboardPostTest.java`

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces:
  - `StreamLeaderboard(ChannelLogReader reader, ZoneId zone)`
  - `Snapshot refresh(TwitchStreamState twitch) throws IOException`: not thread-safe, so call it from one background thread.
  - `record Snapshot(Resolution resolution, List<ChatterTally.Entry> ranking, int total)`
  - `static List<String> LeaderboardPost.formatTop(List<ChatterTally.Entry> ranking)`
  - `static long LeaderboardPost.delayBetweenLinesMs(boolean isPrivileged, int slowModeSecs)`

`refresh` behavior:
1. **First call or after a rebuild:**
   - Do a marker-only pass: `scanFrom(now - 7 days)`, keeping `StatusMarker`s in a cached list.
   - Resolve the range.
   - Build a new `ChatterTally` from `scanFrom(range.start)`, counting `ChatLine`s with `start <= timeMs <= end`.
2. **Later calls:**
   - `readAppended`: count the in-range `ChatLine`s and add any `StatusMarker`s to the cached list.
   - Re-resolve with the cached markers and the current `twitch`.
   - If the range differs from the last one (start, end or isLive), rebuild as in step 1.

The 7-day pass runs only on a rebuild, not on every poll.

- [ ] **Step 1: Write the failing tests:**
  - `LeaderboardPostTest`:
    - `test_formatTop_threeLines`: ranking `[a:342, b:289, c:201, d:5]` gives `["#1 a: 342 messages", "#2 b: 289 messages", "#3 c: 201 messages"]`.
    - `test_formatTop_singularAndFewer`: `[a:1]` gives `["#1 a: 1 message"]`.
    - `test_delay_privileged_zero`
    - `test_delay_slowMode_plusOneSecond` (slow 30 gives 31000)
    - `test_delay_noSlowMode_1500`
  - `StreamLeaderboardTest` (temp dir, daily files):
    - `test_refresh_live_countsFromStart`: lines before and after the Twitch start; only those after are counted.
    - `test_refresh_backfilledLineOutsideRange_excluded`: append an in-range line, then a pre-start line (out of order). The total grows by 1.
    - `test_refresh_endedThenLive_resetsToNewStream`: first refresh not live, with markers giving an ended range. Second refresh is live with a new picnic start. The ranking holds only lines after the new start.
    - `test_refresh_restartAfterShortDrop_includesFirstSegment`: markers LIVE, then OFFLINE 3 minutes before the Twitch start. Lines from the first segment are counted.

- [ ] **Step 2: Run** `./gradlew test --tests "chatty.util.chatlog.*Leaderboard*"`. Expected: compile FAIL.

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run.** Expected: PASS.

- [ ] **Step 5: Commit** `Add stream leaderboard controller and post formatting`.

### Task 6: Dialog, menu wiring, posting

**Files:**
- Create: `src/chatty/gui/components/StreamLeaderboardDialog.java`
- Modify: `src/chatty/gui/MainMenu.java:175` (add `addItem(extra, "dialog.streamLeaderboard");` after `dialog.autoModDialog`)
- Modify: `src/chatty/gui/MainGui.java:494-510` (add `addMenuAction("dialog.streamLeaderboard", "Dialog: Stream Leaderboard (toggle)", ...)`), plus construction near `MainGui.java:269-270` and open/toggle methods like `openModerationLog` at `MainGui.java:3219`
- Modify: `src/chatty/lang/Strings.properties` near line 88 (`menubar.dialog.streamLeaderboard = Stream Leaderboard`)
- Modify: `src/chatty/util/chatlog/ChatLog.java:363` (make `isChanEnabled` public)

**Interfaces:**
- Consumes: `StreamLeaderboard`, `LeaderboardPost`, and from Chatty: `TwitchClient.chatLog.getPath()`, `chatLog.isChanEnabled(channel)`, `client.api.getCachedStreamInfo(stream)`, `StreamInfo.isValid()/getOnline()/getTimeStartedWithPicnic()/getLastOnlineTime()`, `client.getLocalUser(channel)` (`isBroadcaster()/isModerator()/isVip()`), `client.getChannelState(channel).slowMode()`, `client.isChannelOpen(channel)`, `client.textInput(Room, String, null)`, settings `logSplit`, `logSubdirectories`, `logMessage`, `logMessageTemplate` (compare with `getStringDefault`), and `logTimestamp`.
- Produces: `StreamLeaderboardDialog(MainGui owner, TwitchClient client)` and `void showForChannel(Channel channel)`.

Behavior:
- Opening (or reopening) from the menu targets `channels.getLastActiveChannel()`. The title shows the channel.
- While the dialog is visible, a single-thread `ScheduledExecutorService` calls `refresh` every 3 s. Results go to the EDT via `SwingUtilities.invokeLater`. Polling stops on hide, and switching channel builds a new `StreamLeaderboard`.
- Before polling, check support. Show the matching Global Constraints status copy instead of a board when logging is off for the channel (`logMessage` false or `!isChanEnabled`) or the format isn't supported.
- Header: `Live since <h:mm a>, <total> messages`, or `Ended <h:mm a> (<start>-<end>), <total> messages`, or the `Unavailable` reason.
- Body: a `JTable` of rank, name and count.
- "Post top 3":
  - Take a snapshot with `formatTop`, then disable the button.
  - Send line 1 now. Schedule each next line with a `javax.swing.Timer` using `delayBetweenLinesMs`.
  - Before each send, check `isChannelOpen` and stop if the channel is closed.
  - Re-enable the button after the last line or on stop.

- [ ] **Step 1: Implement** the dialog and wiring.
- [ ] **Step 2: Run** `./gradlew build`. Expected: BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 3: Manual check** (follow the chatty-deploy skill to run the app):
  - On a live channel, the board's counts match `chatty_query.py top-chatters -c <channel> --last-session` within the documented differences (`/me` lines, boundary source).
  - Counts climb within about 3 s of new chat.
  - A double click posts 3 lines, not 6.
  - A non-mod channel spaces the lines out.
  - Closing the channel mid-post stops the post.
- [ ] **Step 4: Commit** `Add Stream Leaderboard window to the Extra menu`.

### Task 7: Docs and deploy

**Files:**
- Modify: `docs/DECISIONS.md` (append: date, what, why, tradeoffs: the log as the only source, the marker-based boundary, `-N` files ignored, `/me` counted)
- Modify: `README.md` only if it lists Extra menu features

- [ ] **Step 1: Update docs** as above.
- [ ] **Step 2: Run** `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `Document the stream leaderboard decisions`.
- [ ] **Step 4: Deploy** with the chatty-deploy skill, open the window on a live channel, and confirm it in the running app.
