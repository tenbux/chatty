package chatty.util.chatlog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ChannelLogReaderTest {

    private static final String CHANNEL = "#testchan";
    private static final ZoneId ZONE = ZoneId.of("UTC");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path dir;

    @Before
    public void setUp() {
        dir = folder.getRoot().toPath();
    }

    private static long startOfDay(int month, int day, int hour) {
        return LocalDate.of(2026, month, day).atTime(hour, 0).atZone(ZONE).toInstant().toEpochMilli();
    }

    private void write(String fileName, String content) throws IOException {
        Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
    }

    private void append(String fileName, String content) throws IOException {
        Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private ChannelLogReader reader(String logSplit) {
        return new ChannelLogReader(dir, CHANNEL, logSplit, ZONE);
    }

    private static List<String> scan(ChannelLogReader reader, long fromMs) throws IOException {
        List<String> lines = new ArrayList<>();
        reader.scanFrom(fromMs, lines::add);
        return lines;
    }

    private static List<String> scanBetween(ChannelLogReader reader, long fromMs, long toMs) throws IOException {
        List<String> lines = new ArrayList<>();
        reader.scanBetween(fromMs, toMs, lines::add);
        return lines;
    }

    private void writeThreeDays() throws IOException {
        write("2026-10-01_#testchan.log", "A\n");
        write("2026-10-02_#testchan.log", "B\n");
        write("2026-10-03_#testchan.log", "C\n");
    }

    private static List<String> readNew(ChannelLogReader reader) throws IOException {
        List<String> lines = new ArrayList<>();
        reader.readAppended(lines::add);
        return lines;
    }

    @Test
    public void test_scanFrom_skipsFilesEndingBeforeFrom() throws IOException {
        write("2026-10-29_#testchan.log", "line-29\n");
        write("2026-10-30_#testchan.log", "line-30a\nline-30b\n");
        write("2026-10-31_#testchan.log", "line-31\n");

        List<String> lines = scan(reader("daily"), startOfDay(10, 30, 12));

        assertEquals(List.of("line-30a", "line-30b", "line-31"), lines);
    }

    @Test
    public void test_scanFrom_ignoresSuffixFiles() throws IOException {
        write("2026-10-31_#testchan.log", "main\n");
        write("2026-10-31_#testchan-1.log", "locked-writer\n");

        List<String> lines = scan(reader("daily"), startOfDay(10, 31, 0));

        assertEquals(List.of("main"), lines);
    }

    @Test
    public void test_scanFrom_ignoresOtherChannelsAndNonLogFiles() throws IOException {
        write("2026-10-31_#testchan.log", "mine\n");
        write("2026-10-31_#other.log", "other-channel\n");
        write("2026-10-31_#testchan.txt", "wrong-extension\n");

        List<String> lines = scan(reader("daily"), startOfDay(10, 31, 0));

        assertEquals(List.of("mine"), lines);
    }

    @Test
    public void test_scanFrom_neverSplit_readsSingleFile() throws IOException {
        write("#testchan.log", "one\ntwo\n");
        write("2026-10-31_#testchan.log", "dated-file-is-not-used\n");

        List<String> lines = scan(reader("never"), 0);

        assertEquals(List.of("one", "two"), lines);
    }

    @Test
    public void test_scanFrom_missingDirectory_emitsNothing() throws IOException {
        ChannelLogReader missing = new ChannelLogReader(dir.resolve("absent"), CHANNEL, "daily", ZONE);

        assertTrue(scan(missing, 0).isEmpty());
        assertTrue(readNew(missing).isEmpty());
    }

    @Test
    public void test_scanFrom_noMatchingFiles_emitsNothing() throws IOException {
        assertTrue(scan(reader("daily"), 0).isEmpty());
        assertTrue(scan(reader("never"), 0).isEmpty());
    }

    @Test
    public void test_scanFrom_stripsCarriageReturnAndDecodesUtf8() throws IOException {
        write("2026-10-31_#testchan.log", "café ☃\r\nplain\n");

        List<String> lines = scan(reader("daily"), startOfDay(10, 31, 0));

        assertEquals(List.of("café ☃", "plain"), lines);
    }

    @Test
    public void test_scanFrom_calledAgain_resetsStateAndRereads() throws IOException {
        write("2026-10-31_#testchan.log", "a\nb\n");
        ChannelLogReader reader = reader("daily");
        scan(reader, startOfDay(10, 31, 0));

        List<String> lines = scan(reader, startOfDay(10, 31, 0));

        assertEquals(List.of("a", "b"), lines);
    }

    @Test
    public void test_readAppended_returnsOnlyNewLines() throws IOException {
        write("2026-10-31_#testchan.log", "old\n");
        ChannelLogReader reader = reader("daily");
        scan(reader, startOfDay(10, 31, 0));

        append("2026-10-31_#testchan.log", "new-1\nnew-2\n");

        assertEquals(List.of("new-1", "new-2"), readNew(reader));
        assertTrue(readNew(reader).isEmpty());
    }

    @Test
    public void test_readAppended_partialLineEmittedOnceCompleted() throws IOException {
        write("2026-10-31_#testchan.log", "first\n");
        ChannelLogReader reader = reader("daily");
        scan(reader, startOfDay(10, 31, 0));

        append("2026-10-31_#testchan.log", "[..] <a> hel");
        assertTrue(readNew(reader).isEmpty());

        append("2026-10-31_#testchan.log", "lo\n");
        assertEquals(List.of("[..] <a> hello"), readNew(reader));
    }

    @Test
    public void test_scanFrom_partialFinalLine_notEmittedUntilCompleted() throws IOException {
        write("2026-10-31_#testchan.log", "done\npartial");
        ChannelLogReader reader = reader("daily");

        assertEquals(List.of("done"), scan(reader, startOfDay(10, 31, 0)));

        append("2026-10-31_#testchan.log", "-now-done\n");
        assertEquals(List.of("partial-now-done"), readNew(reader));
    }

    @Test
    public void test_readAppended_partialMultiByteCharacterEmittedIntact() throws IOException {
        byte[] snowman = "☃".getBytes(StandardCharsets.UTF_8);
        Path file = dir.resolve("2026-10-31_#testchan.log");
        Files.write(file, new byte[]{snowman[0], snowman[1]});
        ChannelLogReader reader = reader("daily");
        scan(reader, startOfDay(10, 31, 0));

        Files.write(file, new byte[]{snowman[2], '\n'}, StandardOpenOption.APPEND);

        assertEquals(List.of("☃"), readNew(reader));
    }

    @Test
    public void test_readAppended_picksUpNewDayFile() throws IOException {
        write("2026-10-31_#testchan.log", "day-one\n");
        ChannelLogReader reader = reader("daily");
        scan(reader, startOfDay(10, 31, 0));

        write("2026-11-01_#testchan.log", "day-two-a\nday-two-b\n");
        append("2026-10-31_#testchan.log", "late\n");

        assertEquals(List.of("late", "day-two-a", "day-two-b"), readNew(reader));
        assertTrue(readNew(reader).isEmpty());
    }

    @Test
    public void test_readAppended_ignoresNewFileOlderThanNewestSeen() throws IOException {
        write("2026-10-31_#testchan.log", "day-one\n");
        ChannelLogReader reader = reader("daily");
        scan(reader, startOfDay(10, 31, 0));

        write("2026-10-20_#testchan.log", "backfilled-old-file\n");

        assertTrue(readNew(reader).isEmpty());
    }

    @Test
    public void test_readAppended_afterScanWithNoFiles_picksUpFirstFile() throws IOException {
        ChannelLogReader reader = reader("daily");
        scan(reader, 0);

        write("2026-10-31_#testchan.log", "first\n");

        assertEquals(List.of("first"), readNew(reader));
    }

    @Test
    public void test_readAppended_neverSplit_picksUpFileCreatedAfterScan() throws IOException {
        ChannelLogReader reader = reader("never");
        scan(reader, 0);

        write("#testchan.log", "hello\n");

        assertEquals(List.of("hello"), readNew(reader));
    }

    @Test
    public void test_readAppended_fileShrunk_rereadsFromStart() throws IOException {
        write("2026-10-31_#testchan.log", "old-line-one\nold-line-two\n");
        ChannelLogReader reader = reader("daily");
        scan(reader, startOfDay(10, 31, 0));

        write("2026-10-31_#testchan.log", "new\n");

        assertEquals(List.of("new"), readNew(reader));
        append("2026-10-31_#testchan.log", "after\n");
        assertEquals(List.of("after"), readNew(reader));
    }

    @Test
    public void test_readAppended_fileReplacedByLargerOne_rereadsFromStart() throws IOException {
        write("2026-10-31_#testchan.log", "[10:00] <a> first\n");
        ChannelLogReader reader = reader("daily");
        scan(reader, startOfDay(10, 31, 0));

        write("2026-10-31_#testchan.log", "[11:00] <b> replacement one\n[11:01] <b> replacement two\n");

        assertEquals(List.of("[11:00] <b> replacement one", "[11:01] <b> replacement two"), readNew(reader));
    }

    @Test
    public void test_readAppended_fileShrunk_returnsTrue() throws IOException {
        write("2026-10-31_#testchan.log", "old-line-one\nold-line-two\n");
        ChannelLogReader reader = reader("daily");
        scan(reader, startOfDay(10, 31, 0));

        write("2026-10-31_#testchan.log", "new\n");

        assertTrue(reader.readAppended(line -> { }));
        assertFalse(reader.readAppended(line -> { }));
    }

    @Test
    public void test_readAppended_fileReplacedByLargerOne_returnsTrue() throws IOException {
        write("2026-10-31_#testchan.log", "[10:00] <a> first\n");
        ChannelLogReader reader = reader("daily");
        scan(reader, startOfDay(10, 31, 0));

        write("2026-10-31_#testchan.log", "[11:00] <b> replacement one\n[11:01] <b> replacement two\n");

        assertTrue(reader.readAppended(line -> { }));
    }

    @Test
    public void test_readAppended_normalAppend_returnsFalse() throws IOException {
        write("2026-10-31_#testchan.log", "a\n");
        ChannelLogReader reader = reader("daily");
        scan(reader, startOfDay(10, 31, 0));

        append("2026-10-31_#testchan.log", "b\n");

        assertFalse(reader.readAppended(line -> { }));
    }

    @Test
    public void test_readAppended_consumerThrows_onlyThrownLineIsEmittedAgain() throws IOException {
        write("2026-10-31_#testchan.log", "a\nb\nc\n");
        ChannelLogReader reader = reader("daily");
        List<String> seen = new ArrayList<>();
        try {
            reader.scanFrom(startOfDay(10, 31, 0), line -> {
                if (line.equals("b")) {
                    throw new IllegalStateException("consumer failed");
                }
                seen.add(line);
            });
        } catch (IllegalStateException ex) {
            // Expected: the consumer rejected "b".
        }

        assertEquals(List.of("a"), seen);
        assertEquals(List.of("b", "c"), readNew(reader));
    }

    @Test
    public void test_scanBetween_daily_readsOnlyOverlappingFiles() throws IOException {
        writeThreeDays();

        assertEquals(List.of("B"), scanBetween(reader("daily"), startOfDay(10, 2, 1), startOfDay(10, 2, 23)));
    }

    @Test
    public void test_scanBetween_rangeSpanningFiles_readsEach() throws IOException {
        writeThreeDays();

        assertEquals(List.of("A", "B"), scanBetween(reader("daily"), startOfDay(10, 1, 12), startOfDay(10, 2, 12)));
    }

    @Test
    public void test_scanBetween_never_readsSingleFile() throws IOException {
        write("#testchan.log", "A\nB\n");

        assertEquals(List.of("A", "B"), scanBetween(reader("never"), Long.MIN_VALUE, Long.MAX_VALUE));
    }

    @Test
    public void test_scanBetween_calledTwice_readsFromStartBothTimes() throws IOException {
        writeThreeDays();
        ChannelLogReader reader = reader("daily");

        assertEquals(List.of("B"), scanBetween(reader, startOfDay(10, 2, 1), startOfDay(10, 2, 23)));
        assertEquals(List.of("B"), scanBetween(reader, startOfDay(10, 2, 1), startOfDay(10, 2, 23)));
    }

    @Test
    public void test_oldestStartMs_daily_startOfOldestFile() throws IOException {
        writeThreeDays();

        assertEquals(OptionalLong.of(startOfDay(10, 1, 0)), reader("daily").oldestStartMs());
    }

    @Test
    public void test_oldestStartMs_never_empty() throws IOException {
        write("#testchan.log", "A\n");

        assertEquals(OptionalLong.empty(), reader("never").oldestStartMs());
    }

    @Test
    public void test_oldestStartMs_noFiles_empty() throws IOException {
        assertEquals(OptionalLong.empty(), reader("daily").oldestStartMs());
    }
}
