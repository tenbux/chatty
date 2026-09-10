
package chatty.util;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for ImageCache#saveFile()'s atomic write: it used to
 * copy directly into the destination cache file (truncating it up front),
 * so a failed/interrupted download destroyed a previously-good cached
 * image instead of leaving it untouched.
 */
public class ImageCacheTest {

    private Path tempDir;

    @After
    public void cleanUp() throws IOException {
        if (tempDir != null) {
            try (Stream<Path> stream = Files.walk(tempDir)) {
                List<Path> paths = stream.sorted((a, b) -> b.compareTo(a)).collect(Collectors.toList());
                for (Path p : paths) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    @Test
    public void testSaveFile_success_writesContentAndLeavesNoTempFile() throws IOException {
        tempDir = Files.createTempDirectory("ImageCacheTest");
        Path source = tempDir.resolve("source.bin");
        Files.write(source, "hello world".getBytes(StandardCharsets.UTF_8));
        Path dest = tempDir.resolve("cached.bin");

        boolean result = ImageCache.saveFile(source.toUri().toURL(), dest);

        assertTrue("saveFile must report success", result);
        assertArrayEquals("hello world".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(dest));
        assertNoLeftoverTempFiles(dest);
    }

    @Test
    public void testSaveFile_connectionNeverOpens_doesNotDestroyExistingCacheFile() throws IOException {
        tempDir = Files.createTempDirectory("ImageCacheTest");
        Path dest = tempDir.resolve("cached.bin");
        byte[] originalContent = "previously cached good image data".getBytes(StandardCharsets.UTF_8);
        Files.write(dest, originalContent);

        // A file: URL pointing at a file that doesn't exist -> openConnection()
        // .getInputStream() throws IOException before any byte is read.
        Path missing = tempDir.resolve("does-not-exist.bin");

        boolean result = ImageCache.saveFile(missing.toUri().toURL(), dest);

        assertFalse("saveFile must report failure", result);
        assertArrayEquals("the previously-cached file must be untouched",
                originalContent, Files.readAllBytes(dest));
        assertNoLeftoverTempFiles(dest);
    }

    @Test
    public void testSaveFile_streamFailsMidDownload_doesNotDestroyExistingCacheFile() throws IOException {
        // This is the actual bug scenario: the old code copied directly
        // into the destination, so a download that fails *after* it has
        // already started streaming bytes (unlike a connection that never
        // opens at all) would truncate/corrupt a previously-good cache
        // file instead of leaving it alone.
        tempDir = Files.createTempDirectory("ImageCacheTest");
        Path dest = tempDir.resolve("cached.bin");
        byte[] originalContent = "previously cached good image data".getBytes(StandardCharsets.UTF_8);
        Files.write(dest, originalContent);

        URL flakyUrl = new URL(null, "test-flaky:ignored", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) {
                return new URLConnection(u) {
                    @Override
                    public void connect() {
                    }

                    @Override
                    public InputStream getInputStream() {
                        return new InputStream() {
                            private int position;
                            private final byte[] someBytes = "partial-data-before-failure".getBytes(StandardCharsets.UTF_8);

                            @Override
                            public int read() throws IOException {
                                if (position < someBytes.length) {
                                    return someBytes[position++];
                                }
                                throw new IOException("simulated connection drop mid-download");
                            }
                        };
                    }
                };
            }
        });

        boolean result = ImageCache.saveFile(flakyUrl, dest);

        assertFalse("saveFile must report failure", result);
        assertArrayEquals("the previously-cached file must be untouched, not truncated/corrupted "
                        + "by the partially-streamed replacement",
                originalContent, Files.readAllBytes(dest));
        assertNoLeftoverTempFiles(dest);
    }

    private static void assertNoLeftoverTempFiles(Path dest) throws IOException {
        try (Stream<Path> stream = Files.list(dest.getParent())) {
            List<Path> tmpFiles = stream
                    .filter(p -> p.getFileName().toString().contains(".tmp-"))
                    .collect(Collectors.toList());
            assertTrue("no leftover temp file should remain: "+tmpFiles, tmpFiles.isEmpty());
        }
    }

}
