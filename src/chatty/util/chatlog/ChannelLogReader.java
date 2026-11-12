package chatty.util.chatlog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the on-disk chat log files of a single channel and then follows their
 * growth, emitting only lines that were not emitted before.
 *
 * <p>Call {@link #scanFrom} once, then {@link #readAppended} repeatedly. Lines
 * are UTF-8, emitted without their line terminator (a trailing {@code \r} is
 * dropped), and only once complete.
 *
 * <p>Not thread-safe.
 */
public class ChannelLogReader {

    private static final String LOG_SPLIT_NEVER = "never";
    private static final int CHUNK_SIZE = 64 * 1024;
    private static final int HEAD_SIZE = 64;
    private static final byte[] NO_BYTES = new byte[0];

    /**
     * A file name produced by the writer for a split period, as in
     * {@code 2026-10-31_#chan.log}. The {@code -N.log} variants, which the
     * writer uses when the plain name is locked, deliberately do not match.
     */
    private final Pattern datedFileName;

    private final Path logDir;
    private final String channel;
    private final boolean isSplit;
    private final ZoneId zone;

    /**
     * End of the last complete line read, per file. The bytes of an incomplete
     * final line stay beyond this offset and are re-read once completed.
     */
    private final Map<Path, Long> offsets = new HashMap<>();

    /**
     * The first bytes of each file as first read. Size alone cannot reveal a
     * file that was replaced by a larger one; a changed start does.
     */
    private final Map<Path, byte[]> heads = new HashMap<>();

    /** Date prefix of the newest file seen by the last scan, if any. */
    private LocalDate newestDate;

    /**
     * @param logDir directory holding the log files, including the channel
     * subdirectory if the caller uses one (may be missing)
     * @param channel channel name including the leading {@code #}
     * @param logSplit value of the log split setting
     * @param zone zone the local date prefixes of the file names are in
     */
    public ChannelLogReader(Path logDir, String channel, String logSplit, ZoneId zone) {
        this.logDir = logDir;
        this.channel = channel;
        this.isSplit = !LOG_SPLIT_NEVER.equals(logSplit);
        this.zone = zone;
        this.datedFileName = Pattern.compile(
                "(\\d{4}-\\d{2}-\\d{2})_" + Pattern.quote(channel) + "\\.log");
    }

    /**
     * Forgets all previous progress, then emits every complete line of each
     * file that may hold lines at or after the given time.
     *
     * @param fromMs epoch milliseconds
     * @param lines receives the lines in file order
     * @throws IOException if a file cannot be read
     */
    public void scanFrom(long fromMs, Consumer<String> lines) throws IOException {
        offsets.clear();
        heads.clear();
        newestDate = null;

        List<DatedFile> files = listFiles();
        if (!files.isEmpty()) {
            newestDate = files.get(files.size() - 1).date;
        }
        for (int i = 0; i < files.size(); i++) {
            boolean isLast = i == files.size() - 1;
            // A file lasts until the next one starts, whatever the split period.
            if (isLast || startMillis(files.get(i + 1).date) > fromMs) {
                readFile(files.get(i).path, lines);
            }
        }
        if (!isSplit) {
            readFile(singleFile(), lines);
        }
    }

    /**
     * Emits every complete line of each file that may hold lines between the
     * given times, without following their growth. The lines of an overlapping
     * file are not filtered, so the caller must check their timestamps.
     *
     * <p>Forgets previous progress like {@link #scanFrom}, so
     * {@link #readAppended} has nothing sensible to follow afterwards. Use a
     * separate instance for it.
     *
     * @param fromMs epoch milliseconds, inclusive
     * @param toMs epoch milliseconds, inclusive
     * @param lines receives the lines in file order
     * @throws IOException if a file cannot be read
     */
    public void scanBetween(long fromMs, long toMs, Consumer<String> lines) throws IOException {
        offsets.clear();
        heads.clear();
        newestDate = null;

        if (!isSplit) {
            readFile(singleFile(), lines);
            return;
        }
        List<DatedFile> files = listFiles();
        for (int i = 0; i < files.size(); i++) {
            boolean isLast = i == files.size() - 1;
            // A file lasts until the next one starts, whatever the split period.
            if (startMillis(files.get(i).date) <= toMs
                    && (isLast || startMillis(files.get(i + 1).date) > fromMs)) {
                readFile(files.get(i).path, lines);
            }
        }
    }

    /**
     * @return the start of the day of the oldest dated file, or empty if the
     * log is not split or has no dated files
     * @throws IOException if the directory cannot be listed
     */
    public OptionalLong oldestStartMs() throws IOException {
        List<DatedFile> files = listFiles();
        return files.isEmpty() ? OptionalLong.empty() : OptionalLong.of(startMillis(files.get(0).date));
    }

    /**
     * Emits the complete lines added since the last {@link #scanFrom} or
     * {@code readAppended}. Covers the files the scan selected, plus files that
     * appeared since and are not older than the newest file the scan saw.
     *
     * @param lines receives the lines in file order
     * @return true if any file was read again from its start because it shrank
     * or no longer starts with the bytes first read; the lines of such a file
     * were emitted before, so the caller's totals may now count them twice
     * @throws IOException if a file cannot be read
     */
    public boolean readAppended(Consumer<String> lines) throws IOException {
        boolean hasReset = false;
        List<Path> toRead = new ArrayList<>(offsets.keySet());
        if (isSplit) {
            for (DatedFile file : listFiles()) {
                boolean isNew = !offsets.containsKey(file.path);
                if (isNew && (newestDate == null || !file.date.isBefore(newestDate))) {
                    toRead.add(file.path);
                    if (newestDate == null || file.date.isAfter(newestDate)) {
                        newestDate = file.date;
                    }
                }
            }
            // File names sort by date, which is the order lines were written.
            toRead.sort(Comparator.naturalOrder());
        } else if (!toRead.contains(singleFile())) {
            toRead.add(singleFile());
        }
        for (Path path : toRead) {
            hasReset |= readFile(path, lines);
        }
        return hasReset;
    }

    private Path singleFile() {
        return logDir.resolve(channel + ".log");
    }

    private long startMillis(LocalDate date) {
        return date.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    private List<DatedFile> listFiles() throws IOException {
        List<DatedFile> result = new ArrayList<>();
        if (!isSplit || !Files.isDirectory(logDir)) {
            return result;
        }
        try (Stream<Path> children = Files.list(logDir)) {
            for (Path path : (Iterable<Path>) children::iterator) {
                Matcher matcher = datedFileName.matcher(path.getFileName().toString());
                if (matcher.matches() && Files.isRegularFile(path)) {
                    try {
                        result.add(new DatedFile(path, LocalDate.parse(matcher.group(1))));
                    } catch (DateTimeParseException ex) {
                        // Digits shaped like a date but not a real one (2026-13-45): not ours.
                    }
                }
            }
        }
        result.sort(Comparator.comparing((DatedFile file) -> file.date));
        return result;
    }

    /**
     * Reads from the stored offset to the end of the file as it is now, so a
     * file that keeps growing cannot keep this call busy. A file that is now
     * shorter than the stored offset, or no longer starts with the bytes first
     * read, was truncated or replaced and is read again from the start.
     *
     * @return whether the file was read again from the start for that reason
     */
    private boolean readFile(Path path, Consumer<String> lines) throws IOException {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        boolean isReset = false;
        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ)) {
            long end = fileChannel.size();
            long position = offsets.getOrDefault(path, 0L);
            if (position > 0 && (end < position || !hasSameHead(path, fileChannel))) {
                position = 0;
                isReset = true;
                heads.remove(path);
            }
            // Recorded up front so a file that is empty or has no complete line yet is still followed.
            offsets.put(path, position);

            ByteBuffer buffer = ByteBuffer.allocate(CHUNK_SIZE);
            byte[] carry = NO_BYTES;
            while (position < end) {
                buffer.clear();
                buffer.limit((int) Math.min(CHUNK_SIZE, end - position));
                int read = fileChannel.read(buffer, position);
                if (read < 0) {
                    break;
                }
                // File offset of data[0]; carry holds the bytes just before this read.
                long dataStart = position - carry.length;
                position += read;

                byte[] data = new byte[carry.length + read];
                System.arraycopy(carry, 0, data, 0, carry.length);
                System.arraycopy(buffer.array(), 0, data, carry.length, read);
                int lineStart = 0;
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == '\n') {
                        emit(data, lineStart, i, lines);
                        lineStart = i + 1;
                        // After each line, so a consumer that throws only gets the line it threw on again.
                        offsets.put(path, dataStart + lineStart);
                    }
                }
                carry = Arrays.copyOfRange(data, lineStart, data.length);
            }
            rememberHead(path, fileChannel);
        }
        return isReset;
    }

    private boolean hasSameHead(Path path, FileChannel fileChannel) throws IOException {
        byte[] known = heads.get(path);
        return known == null || Arrays.equals(known, readHead(fileChannel, known.length));
    }

    private void rememberHead(Path path, FileChannel fileChannel) throws IOException {
        long consumed = offsets.get(path);
        if (consumed > 0 && !heads.containsKey(path)) {
            heads.put(path, readHead(fileChannel, (int) Math.min(consumed, HEAD_SIZE)));
        }
    }

    private static byte[] readHead(FileChannel fileChannel, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining() && fileChannel.read(buffer, buffer.position()) >= 0) {
            // Keep filling; a short read is not the end of the file.
        }
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private static void emit(byte[] data, int start, int endExclusive, Consumer<String> lines) {
        if (endExclusive > start && data[endExclusive - 1] == '\r') {
            endExclusive--;
        }
        lines.accept(new String(data, start, endExclusive - start, StandardCharsets.UTF_8));
    }

    private static class DatedFile {

        final Path path;
        final LocalDate date;

        DatedFile(Path path, LocalDate date) {
            this.path = path;
            this.date = date;
        }
    }
}
