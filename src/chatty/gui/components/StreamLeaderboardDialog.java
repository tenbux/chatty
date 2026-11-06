package chatty.gui.components;

import chatty.Room;
import chatty.TwitchClient;
import chatty.User;
import chatty.gui.MainGui;
import chatty.util.MiscUtil;
import chatty.util.api.StreamInfo;
import chatty.util.chatlog.ChannelLogReader;
import chatty.util.chatlog.ChatterTally;
import chatty.util.chatlog.LeaderboardPost;
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
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 * Non-modal window showing who chatted most in the current (or most recent)
 * stream of one channel, with buttons to post the highlighted rows to that
 * channel or copy them (or the whole list) to the clipboard.
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

    public StreamLeaderboardDialog(MainGui owner, TwitchClient client) {
        super(owner);
        this.client = client;
        this.settings = client.settings;

        header.setBorder(BorderFactory.createEmptyBorder(6, 8, 2, 8));
        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));

        JPanel top = new JPanel(new BorderLayout());
        top.add(header, BorderLayout.NORTH);
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
        Path dir = client.chatLog.getPath();
        if (dir == null) {
            return null;
        }
        if (settings.getBoolean("logSubdirectories")) {
            dir = dir.resolve(channelName);
        }
        ChannelLogReader reader = new ChannelLogReader(dir, channelName, settings.getString("logSplit"), zone);
        return new StreamLeaderboard(reader, zone, System::currentTimeMillis);
    }

    /** Poll-thread state of one polling session. */
    private static class PollState {
        /** Whether the previous poll failed, so a failure that persists is logged only once. */
        boolean isFailing;
    }

    /** Runs on the poll thread. */
    private void poll(String channelName, StreamLeaderboard board, Object current, PollState pollState) {
        try {
            String unsupported = findUnsupportedReason(channelName, board);
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

    private String findUnsupportedReason(String channelName, StreamLeaderboard board) {
        if (board == null || !settings.getBoolean("logMessage") || !client.chatLog.isChanEnabled(channelName)) {
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
            status.setText(statusText.isEmpty() ? " " : statusText);
            if (snapshot != null) {
                header.setText(describe(snapshot));
                copyRange = snapshot.resolution() instanceof StreamRange range ? range : null;
                copyTotal = snapshot.total();
                showRanking(snapshot.ranking());
            } else if (!isKeepingBoard) {
                // A failed read keeps the last good board; an unsupported setup has no valid board to keep.
                header.setText(" ");
                copyRange = null;
                showRanking(List.of());
            }
        });
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
            // Thrown when another app holds the clipboard; the next poll clears this status.
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
