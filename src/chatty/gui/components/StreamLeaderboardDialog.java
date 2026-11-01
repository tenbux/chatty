package chatty.gui.components;

import chatty.Room;
import chatty.TwitchClient;
import chatty.User;
import chatty.gui.MainGui;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Non-modal window showing who chatted most in the current (or most recent)
 * stream of one channel, with a button to post the top three to that channel.
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

    private final TwitchClient client;
    private final Settings settings;
    private final ZoneId zone = ZoneId.systemDefault();

    private final JLabel header = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final RankingModel model = new RankingModel();
    private final JButton postButton = new JButton("Post top 3");

    private ScheduledExecutorService poller;
    private Channel channel;
    /** Identifies the current polling session, so late results from a stopped one are dropped. */
    private Object session;
    private boolean isPosting;
    private List<ChatterTally.Entry> ranking = List.of();

    public StreamLeaderboardDialog(MainGui owner, TwitchClient client) {
        super(owner);
        this.client = client;
        this.settings = client.settings;

        header.setBorder(BorderFactory.createEmptyBorder(6, 8, 2, 8));
        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));

        JPanel top = new JPanel(new BorderLayout());
        top.add(header, BorderLayout.NORTH);
        top.add(status, BorderLayout.SOUTH);

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(2).setMaxWidth(90);

        postButton.setEnabled(false);
        postButton.addActionListener(e -> postTop());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
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
                showRanking(snapshot.ranking());
            } else if (!isKeepingBoard) {
                // A failed read keeps the last good board; an unsupported setup has no valid board to keep.
                header.setText(" ");
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
        ranking = entries;
        model.setEntries(entries);
        updatePostButton();
    }

    private void updatePostButton() {
        postButton.setEnabled(!isPosting && !ranking.isEmpty());
    }

    /** Sends the first line now and the rest paced; runs on the EDT. */
    private void postTop() {
        List<String> lines = LeaderboardPost.formatTop(ranking);
        if (isPosting || lines.isEmpty() || channel == null) {
            return;
        }
        String channelName = channel.getChannel();
        Room room = channel.getRoom();
        User localUser = client.getLocalUser(channelName);
        boolean isPrivileged = localUser != null
                && (localUser.isBroadcaster() || localUser.isModerator() || localUser.isVip());
        long delay = LeaderboardPost.delayBetweenLinesMs(isPrivileged, client.getChannelState(channelName).slowMode());

        isPosting = true;
        updatePostButton();
        if (!sendLine(channelName, room, lines.get(0)) || lines.size() == 1) {
            finishPosting();
            return;
        }
        int[] next = {1};
        Timer timer = new Timer((int) delay, null);
        timer.addActionListener(e -> {
            if (!sendLine(channelName, room, lines.get(next[0])) || ++next[0] >= lines.size()) {
                timer.stop();
                finishPosting();
            }
        });
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

    private void finishPosting() {
        isPosting = false;
        updatePostButton();
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
