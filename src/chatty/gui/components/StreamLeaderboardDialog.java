package chatty.gui.components;

import chatty.Room;
import chatty.TwitchClient;
import chatty.User;
import chatty.gui.GuiUtil;
import chatty.gui.MainGui;
import chatty.util.MiscUtil;
import chatty.util.api.StreamInfo;
import chatty.util.chatlog.ChannelLogReader;
import chatty.util.chatlog.ChatterTally;
import chatty.util.chatlog.LeaderboardPost;
import chatty.util.chatlog.PastStreamFinder;
import chatty.util.chatlog.PastStreamFinder.PastStream;
import chatty.util.chatlog.PastStreamPages;
import chatty.util.chatlog.StreamLeaderboard;
import chatty.util.chatlog.StreamLeaderboard.Snapshot;
import chatty.util.chatlog.StreamRangeResolver.Resolution;
import chatty.util.chatlog.StreamRangeResolver.StreamRange;
import chatty.util.chatlog.StreamRangeResolver.TwitchStreamState;
import chatty.util.chatlog.StreamRangeResolver.Unavailable;
import chatty.util.settings.Settings;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * Non-modal window showing who chatted most in the current (or most recent)
 * stream of one channel, with buttons to post the highlighted rows to that
 * channel or copy them (or the whole list) to the clipboard, and to step back
 * and forward through earlier streams.
 * Counts come from the channel's log files, read on a background thread while
 * the window is visible.
 */
public class StreamLeaderboardDialog extends JDialog {

    private static final Logger LOGGER = Logger.getLogger(StreamLeaderboardDialog.class.getName());

    private static final long POLL_INTERVAL_SECONDS = 3;

    private static final String READ_ERROR = "Could not read the log files";

    private static final String UNEXPECTED_ERROR = "Leaderboard error, see the debug log";

    private static final String DEFAULT_TIMESTAMP = "[yyyy-MM-dd HH:mm:ss]";

    private static final String NOT_A_CHANNEL = "Open a channel tab to see its leaderboard";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d h:mm a");

    private static final String COPY_LABEL = "Copy";

    private static final String COPIED_LABEL = "Copied";

    private static final int COPIED_FEEDBACK_MS = 1500;

    private static final String POST_LABEL = "Post to chat";

    private static final String STOP_LABEL = "Stop";

    private static final String LOADING_PAST = "Looking for the previous stream...";

    private static final String NO_OLDER_PAST = "No earlier streams in the logs";

    private final TwitchClient client;
    private final Settings settings;
    private final ZoneId zone = ZoneId.systemDefault();

    private final JLabel header = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final RankingModel model = new RankingModel();
    private final JTable table = new JTable(model);
    private final JButton postButton = new JButton(POST_LABEL);
    private final JButton copyButton = new JButton(COPIED_LABEL);
    private final Timer copiedReset = new Timer(COPIED_FEEDBACK_MS, e -> copyButton.setText(COPY_LABEL));
    private final JButton backButton = new JButton("\u25C0");
    private final JButton forwardButton = new JButton("\u25B6");
    private final PastStreamPages pages = new PastStreamPages();

    private ScheduledExecutorService poller;
    private Channel channel;
    /** Identifies the current polling session, so late results from a stopped one are dropped. */
    private Object session;
    /** Sends the remaining lines of a post in progress, null when not posting. */
    private Timer postTimer;
    private List<ChatterTally.Entry> ranking = List.of();
    /** Stream named in the first line of copied text, null when there is none. */
    private StreamRange copyRange;
    private int copyTotal;
    /** Latest poll result for page 0, kept while a past page is shown; null when there is no board. */
    private Snapshot newestSnapshot;
    /** Latest poll status text for page 0, empty when there is none. */
    private String newestStatus = "";
    /** Start of the latest page-0 stream range seen, empty until one arrives. */
    private OptionalLong newestRangeStartMs = OptionalLong.empty();
    /**
     * Page 0's search anchor while no range has arrived, fixed at the first Back
     * so a search that found nothing keeps Back disabled.
     */
    private OptionalLong noRangeAnchorMs = OptionalLong.empty();
    /** Runs past-stream searches, created on first use; null when none was started. */
    private ExecutorService pastExecutor;
    /** Identifies the current past-stream search, so late results from a cancelled one are dropped. */
    private Object pastToken;
    private boolean isLoadingPast;

    public StreamLeaderboardDialog(MainGui owner, TwitchClient client) {
        super(owner);
        this.client = client;
        this.settings = client.settings;

        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));

        backButton.setToolTipText("Previous stream");
        backButton.setMargin(GuiUtil.SMALLER_BUTTON_INSETS);
        backButton.setEnabled(false);
        backButton.addActionListener(e -> goBack());
        forwardButton.setToolTipText("Next stream");
        forwardButton.setMargin(GuiUtil.SMALLER_BUTTON_INSETS);
        forwardButton.setEnabled(false);
        forwardButton.addActionListener(e -> goForward());
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        nav.setBorder(BorderFactory.createEmptyBorder(6, 6, 2, 0));
        nav.add(backButton);
        nav.add(forwardButton);
        header.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 8));

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.add(nav, BorderLayout.WEST);
        headerRow.add(header, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout());
        top.add(headerRow, BorderLayout.NORTH);
        top.add(status, BorderLayout.SOUTH);

        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(2).setMaxWidth(90);

        // So Cmd/Ctrl+C copies the same text as the button instead of raw table cells.
        table.getActionMap().put(TransferHandler.getCopyAction().getValue(Action.NAME), new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copy();
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> updateButtons());

        // Sized for the longer label, so showing "Stop" doesn't shift the buttons.
        postButton.setPreferredSize(postButton.getPreferredSize());
        postButton.setToolTipText("Post the highlighted rows to chat, one message each");
        postButton.setEnabled(false);
        postButton.addActionListener(e -> {
            if (postTimer != null) {
                stopPosting();
            } else {
                postSelected();
            }
        });
        // Sized for the longer label, so showing "Copied" doesn't shift the buttons.
        copyButton.setPreferredSize(copyButton.getPreferredSize());
        copyButton.setText(COPY_LABEL);
        copyButton.setToolTipText("Copy the highlighted rows, or the whole list if none are highlighted");
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copy());
        copiedReset.setRepeats(false);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(copyButton);
        bottom.add(postButton);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(340, 420));
        pack();
    }

    /**
     * Shows the window for the given channel and starts reading its logs.
     * Safe to call while already visible, which switches to that channel.
     * Must be called on the EDT.
     *
     * @param target the channel to show, may be null if no channel is open
     */
    public void showForChannel(Channel target) {
        if (target == null) {
            return;
        }
        stopPolling();
        channel = target;
        resetPast();
        setTitle("Stream Leaderboard: " + target.getChannel());
        copyRange = null;
        showRanking(List.of());
        header.setText(" ");
        status.setText(" ");
        super.setVisible(true);
        // Whisper and other non-channel tabs have no channel log or stream to read.
        if (!target.getChannel().startsWith("#")) {
            status.setText(NOT_A_CHANNEL);
            return;
        }
        startPolling();
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            stopPolling();
            resetPast();
        }
        super.setVisible(visible);
    }

    private void startPolling() {
        String channelName = channel.getChannel();
        Object current = new Object();
        session = current;
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "StreamLeaderboard-" + channelName);
            thread.setDaemon(true);
            return thread;
        });
        poller = executor;
        // Built here and used only by the poll thread afterwards, as StreamLeaderboard is not thread-safe.
        StreamLeaderboard board = createBoard(channelName);
        PollState pollState = new PollState();
        executor.scheduleWithFixedDelay(() -> poll(channelName, board, current, pollState),
                0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopPolling() {
        session = null;
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
    }

    private StreamLeaderboard createBoard(String channelName) {
        ChannelLogReader reader = createReader(channelName);
        return reader == null ? null : new StreamLeaderboard(reader, zone, System::currentTimeMillis);
    }

    /** Returns null when chat logging has no directory. */
    private ChannelLogReader createReader(String channelName) {
        Path dir = client.chatLog.getPath();
        if (dir == null) {
            return null;
        }
        if (settings.getBoolean("logSubdirectories")) {
            dir = dir.resolve(channelName);
        }
        return new ChannelLogReader(dir, channelName, settings.getString("logSplit"), zone);
    }

    /** Poll-thread state of one polling session. */
    private static class PollState {
        /** Whether the previous poll failed, so a failure that persists is logged only once. */
        boolean isFailing;
    }

    /** Runs on the poll thread. */
    private void poll(String channelName, StreamLeaderboard board, Object current, PollState pollState) {
        try {
            String unsupported = findUnsupportedReason(channelName, board != null);
            if (unsupported != null) {
                publish(current, null, unsupported, false);
            } else {
                publish(current, board.refresh(readTwitchState(channelName)), "", false);
            }
            pollState.isFailing = false;
        } catch (IOException ex) {
            handlePollFailure(channelName, current, pollState, ex, "could not read log files", READ_ERROR);
        } catch (RuntimeException ex) {
            // Must not escape: an exception would silently cancel all later polls.
            handlePollFailure(channelName, current, pollState, ex, "unexpected error", UNEXPECTED_ERROR);
        }
    }

    private void handlePollFailure(String channelName, Object current, PollState pollState,
            Exception ex, String logReason, String statusText) {
        // Closing the window interrupts the poll thread, which can surface as a failed read.
        if (ex instanceof ClosedByInterruptException || Thread.currentThread().isInterrupted()) {
            return;
        }
        if (!pollState.isFailing) {
            LOGGER.log(Level.WARNING, "Stream leaderboard: " + logReason + " for " + channelName, ex);
            pollState.isFailing = true;
        }
        publish(current, null, statusText, true);
    }

    private String findUnsupportedReason(String channelName, boolean hasLogDir) {
        if (!hasLogDir || !settings.getBoolean("logMessage") || !client.chatLog.isChanEnabled(channelName)) {
            return "Logging is off for this channel";
        }
        boolean isDefaultTemplate = settings.getString("logMessageTemplate")
                .equals(settings.getStringDefault("logMessageTemplate"));
        boolean isDefaultTimestamp = DEFAULT_TIMESTAMP.equals(settings.getString("logTimestamp"));
        if (!isDefaultTemplate || !isDefaultTimestamp) {
            return "Log format not supported (custom timestamp or message template)";
        }
        return null;
    }

    private TwitchStreamState readTwitchState(String channelName) {
        StreamInfo info = client.api.getCachedStreamInfo(channelName.substring(1));
        if (info == null) {
            return new TwitchStreamState(false, -1, -1);
        }
        return new TwitchStreamState(info.isValidEnough() && info.getOnline(),
                info.getTimeStartedWithPicnic(), info.getLastOnlineTime());
    }

    /** Hands a poll result to the EDT unless its polling session has ended. */
    private void publish(Object current, Snapshot snapshot, String statusText, boolean isKeepingBoard) {
        SwingUtilities.invokeLater(() -> {
            if (session != current) {
                return;
            }
            newestStatus = statusText;
            if (snapshot != null) {
                newestSnapshot = snapshot;
                if (snapshot.resolution() instanceof StreamRange range) {
                    newestRangeStartMs = OptionalLong.of(range.startMs());
                    noRangeAnchorMs = OptionalLong.empty();
                    pages.reanchor(range.startMs());
                }
            } else if (!isKeepingBoard) {
                // A failed read keeps the last good board; an unsupported setup has no valid board to keep.
                newestSnapshot = null;
            }
            // A past page shown stays as it is; a reanchor that dropped held pages only changes the buttons.
            if (pages.index() == 0) {
                // A search can outlast a poll interval, and the poll status would replace its loading message.
                if (!isLoadingPast) {
                    showStatus(newestStatus);
                }
                showNewestPage();
            }
            updateNavButtons();
        });
    }

    private void showStatus(String text) {
        status.setText(text.isEmpty() ? " " : text);
    }

    /** Shows page 0 from the latest poll result. */
    private void showNewestPage() {
        header.setText(newestSnapshot == null ? " " : describe(newestSnapshot));
        copyRange = newestSnapshot != null && newestSnapshot.resolution() instanceof StreamRange range ? range : null;
        copyTotal = newestSnapshot == null ? 0 : newestSnapshot.total();
        showRanking(newestSnapshot == null ? List.of() : newestSnapshot.ranking());
    }

    private void showPastPage(PastStream page) {
        header.setText(LeaderboardPost.formatPastHeader(page.range(), page.total(), zone));
        copyRange = page.range();
        copyTotal = page.total();
        showRanking(page.ranking());
        showStatus("");
    }

    /** Shows the page {@code pages} points at, with no rows highlighted. */
    private void showCurrentPage() {
        DefaultListSelectionModel selection = (DefaultListSelectionModel) table.getSelectionModel();
        // showRanking skips a refresh mid-drag, and a past page gets no later poll to catch up.
        selection.setValueIsAdjusting(false);
        // Cleared first so showRanking doesn't reselect by name, and so an equal ranking still drops the highlight.
        table.clearSelection();
        PastStream page = pages.current();
        if (page == null) {
            showNewestPage();
            showStatus(newestStatus);
        } else {
            showPastPage(page);
        }
        updateNavButtons();
    }

    /** Page 0's search anchor: its latest stream start, else the time of the first Back. */
    private long newestBeforeMs() {
        if (newestRangeStartMs.isPresent()) {
            return newestRangeStartMs.getAsLong();
        }
        return noRangeAnchorMs.orElseGet(System::currentTimeMillis);
    }

    private boolean isBackAllowed() {
        if (isLoadingPast || channel == null) {
            return false;
        }
        String channelName = channel.getChannel();
        if (!channelName.startsWith("#")
                || findUnsupportedReason(channelName, client.chatLog.getPath() != null) != null) {
            return false;
        }
        return pages.hasCachedOlder() || pages.canSearchOlder(newestBeforeMs());
    }

    private void updateNavButtons() {
        backButton.setEnabled(isBackAllowed());
        forwardButton.setEnabled(!isLoadingPast && pages.index() > 0);
    }

    private void goBack() {
        if (!isBackAllowed()) {
            updateNavButtons();
            return;
        }
        if (pages.hasCachedOlder()) {
            pages.back();
            showCurrentPage();
            return;
        }
        String channelName = channel.getChannel();
        ChannelLogReader reader = createReader(channelName);
        if (reader == null) {
            updateNavButtons();
            return;
        }
        if (pages.index() == 0 && newestRangeStartMs.isEmpty() && noRangeAnchorMs.isEmpty()) {
            noRangeAnchorMs = OptionalLong.of(System.currentTimeMillis());
        }
        long anchor = pages.backAnchor(newestBeforeMs());
        // A fresh reader, since each search resets the reader's progress and the poll thread owns its own.
        PastStreamFinder finder = new PastStreamFinder(reader, channelName, zone);
        Object token = new Object();
        pastToken = token;
        isLoadingPast = true;
        showStatus(LOADING_PAST);
        updateNavButtons();
        if (pastExecutor == null) {
            pastExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "StreamLeaderboardPast-" + channelName);
                thread.setDaemon(true);
                return thread;
            });
        }
        pastExecutor.execute(() -> findPast(channelName, finder, anchor, token));
    }

    /** Runs on the past-stream thread. */
    private void findPast(String channelName, PastStreamFinder finder, long anchor, Object token) {
        try {
            Optional<PastStream> found = finder.findBefore(anchor);
            SwingUtilities.invokeLater(() -> finishPastSearch(token, anchor, found));
        } catch (IOException | RuntimeException ex) {
            // Closing the window or switching channel interrupts the search, which can surface as a failed read.
            if (ex instanceof ClosedByInterruptException || Thread.currentThread().isInterrupted()) {
                return;
            }
            LOGGER.log(Level.WARNING, "Stream leaderboard: could not find the previous stream for " + channelName, ex);
            SwingUtilities.invokeLater(() -> failPastSearch(token));
        }
    }

    private void finishPastSearch(Object token, long anchor, Optional<PastStream> found) {
        if (pastToken != token) {
            return;
        }
        isLoadingPast = false;
        // Page 0's stream changed while searching, so the result may skip or repeat the stream that just ended.
        if (pages.backAnchor(newestBeforeMs()) != anchor) {
            showStatus(pages.index() == 0 ? newestStatus : "");
            goBack();
            return;
        }
        if (found.isPresent()) {
            pages.addOlderAndShow(found.get());
            showCurrentPage();
        } else {
            pages.markNoOlder(anchor);
            showStatus(NO_OLDER_PAST);
            updateNavButtons();
        }
    }

    private void failPastSearch(Object token) {
        if (pastToken != token) {
            return;
        }
        isLoadingPast = false;
        showStatus(READ_ERROR);
        updateNavButtons();
    }

    private void goForward() {
        if (isLoadingPast || pages.index() == 0) {
            updateNavButtons();
            return;
        }
        pages.forward();
        showCurrentPage();
    }

    /** Cancels any past-stream search and forgets past pages and page 0's last result. */
    private void resetPast() {
        pastToken = null;
        if (pastExecutor != null) {
            pastExecutor.shutdownNow();
            pastExecutor = null;
        }
        isLoadingPast = false;
        pages.reset();
        newestSnapshot = null;
        newestStatus = "";
        newestRangeStartMs = OptionalLong.empty();
        noRangeAnchorMs = OptionalLong.empty();
        updateNavButtons();
    }

    private String describe(Snapshot snapshot) {
        Resolution resolution = snapshot.resolution();
        if (resolution instanceof Unavailable unavailable) {
            return unavailable.reason();
        }
        StreamRange range = (StreamRange) resolution;
        String total = snapshot.total() + (snapshot.total() == 1 ? " message" : " messages");
        if (range.isLive()) {
            return "Live since " + TIME_FORMAT.format(toLocal(range.startMs())) + ", " + total;
        }
        // An older stream needs its date, or "Ended 9:00 PM" reads as today.
        boolean isEndedToday = toLocal(range.endMs()).toLocalDate().equals(LocalDate.now(zone));
        DateTimeFormatter format = isEndedToday ? TIME_FORMAT : DATE_TIME_FORMAT;
        String end = format.format(toLocal(range.endMs()));
        return "Ended " + end + " (" + format.format(toLocal(range.startMs())) + "-" + end + "), " + total;
    }

    private ZonedDateTime toLocal(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(zone);
    }

    private void showRanking(List<ChatterTally.Entry> entries) {
        // Replacing the table data clears the selection, so skip it when nothing changed.
        if (entries.equals(ranking)) {
            return;
        }
        DefaultListSelectionModel selection = (DefaultListSelectionModel) table.getSelectionModel();
        // Refreshing mid-drag would end the drag; the next poll still sees a changed ranking and catches up.
        if (selection.getValueIsAdjusting()) {
            return;
        }
        Set<String> selectedNames = selectedNames();
        String anchorName = nameAt(selection.getAnchorSelectionIndex());
        String leadName = nameAt(selection.getLeadSelectionIndex());
        ranking = entries;
        model.setEntries(entries);
        if (!selectedNames.isEmpty()) {
            // Reselect by name, since a highlighted chatter's rank can change between polls.
            for (int row = 0; row < entries.size(); row++) {
                if (selectedNames.contains(nameKey(entries.get(row)))) {
                    table.addRowSelectionInterval(row, row);
                }
            }
            // Reselecting moves the anchor to the last row added, which would make Shift+click extend from there.
            int anchor = rowOf(anchorName);
            int lead = rowOf(leadName);
            if (anchor >= 0 && lead >= 0) {
                selection.setAnchorSelectionIndex(anchor);
                selection.moveLeadSelectionIndex(lead);
            }
        }
        updateButtons();
    }

    private String nameAt(int row) {
        return row >= 0 && row < ranking.size() ? nameKey(ranking.get(row)) : null;
    }

    private int rowOf(String name) {
        for (int row = 0; row < ranking.size(); row++) {
            if (nameKey(ranking.get(row)).equals(name)) {
                return row;
            }
        }
        return -1;
    }

    private Set<String> selectedNames() {
        Set<String> names = new HashSet<>();
        for (int row : table.getSelectedRows()) {
            names.add(nameKey(ranking.get(row)));
        }
        return names;
    }

    private static String nameKey(ChatterTally.Entry entry) {
        return entry.name().toLowerCase(Locale.ROOT);
    }

    private void updateButtons() {
        boolean isPosting = postTimer != null;
        postButton.setText(isPosting ? STOP_LABEL : POST_LABEL);
        postButton.setEnabled(isPosting || table.getSelectedRowCount() > 0);
        copyButton.setEnabled(!ranking.isEmpty());
    }

    /** Copies the highlighted rows, or all rows if none are highlighted, under a line naming the stream. */
    private void copy() {
        if (ranking.isEmpty()) {
            return;
        }
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) {
            rows = IntStream.range(0, ranking.size()).toArray();
        }
        List<String> lines = new ArrayList<>();
        if (copyRange != null) {
            // Built at copy time so a live stream's run time is current.
            lines.add(LeaderboardPost.formatCopyHeader(channel.getChannel(), copyRange, copyTotal, zone,
                    System.currentTimeMillis()));
        }
        lines.addAll(formatRows(rows));
        try {
            MiscUtil.copyToClipboard(String.join("\n", lines));
        } catch (IllegalStateException ex) {
            // Thrown when another app holds the clipboard; the next poll or page change clears this status.
            LOGGER.log(Level.WARNING, "Stream leaderboard: could not copy to the clipboard", ex);
            status.setText("Could not copy, the clipboard is busy");
            return;
        }
        copyButton.setText(COPIED_LABEL);
        copiedReset.restart();
    }

    private List<String> formatRows(int[] rows) {
        List<String> lines = new ArrayList<>();
        for (int row : rows) {
            lines.add(LeaderboardPost.formatLine(row + 1, ranking.get(row)));
        }
        return lines;
    }

    /** Sends the highlighted rows, the first now and the rest paced; runs on the EDT. */
    private void postSelected() {
        // Taken now, so changing the highlight or a refresh mid-post doesn't change what is sent.
        List<String> lines = formatRows(table.getSelectedRows());
        if (postTimer != null || lines.isEmpty() || channel == null) {
            return;
        }
        String channelName = channel.getChannel();
        Room room = channel.getRoom();
        User localUser = client.getLocalUser(channelName);
        boolean isPrivileged = localUser != null
                && (localUser.isBroadcaster() || localUser.isModerator() || localUser.isVip());
        long delay = LeaderboardPost.delayBetweenLinesMs(isPrivileged, client.getChannelState(channelName).slowMode());

        if (!sendLine(channelName, room, lines.get(0)) || lines.size() == 1) {
            return;
        }
        int[] next = {1};
        Timer timer = new Timer((int) delay, null);
        timer.addActionListener(e -> {
            if (!sendLine(channelName, room, lines.get(next[0])) || ++next[0] >= lines.size()) {
                stopPosting();
            }
        });
        postTimer = timer;
        updateButtons();
        timer.start();
    }

    /** Returns false if the channel was left, in which case nothing is sent. */
    private boolean sendLine(String channelName, Room room, String line) {
        if (!client.isChannelOpen(channelName)) {
            return false;
        }
        client.textInput(room, line, null);
        return true;
    }

    /** Cancels the lines of the current post not yet sent. */
    private void stopPosting() {
        if (postTimer != null) {
            postTimer.stop();
            postTimer = null;
        }
        updateButtons();
    }

    private static class RankingModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"#", "Name", "Messages"};

        private List<ChatterTally.Entry> entries = new ArrayList<>();

        void setEntries(List<ChatterTally.Entry> newEntries) {
            entries = new ArrayList<>(newEntries);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 1 ? String.class : Integer.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            ChatterTally.Entry entry = entries.get(row);
            return switch (column) {
                case 0 -> row + 1;
                case 1 -> entry.name();
                default -> entry.count();
            };
        }
    }
}
