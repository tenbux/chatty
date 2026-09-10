
package chatty.gui.components.updating;

import chatty.gui.components.updating.FileDownloader.FileDownloaderListener;

import org.junit.After;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for the auto-updater hardening fixes: a download that
 * doesn't deliver as many bytes as the server declared via Content-Length
 * must be reported as an error, not silently treated as complete (this is
 * the difference between an update installer that's safe to run and one
 * that's a corrupt/partial file being executed). Also covers
 * UpdateDialog#isHttps(), the extracted https-only guard on the asset URL.
 *
 * Uses a real local HttpServer (JDK built-in, no test dependency needed) so
 * FileDownloader's actual production run() method is exercised end to end,
 * not a mock.
 */
public class FileDownloaderTest {

    private HttpServer server;
    private Path downloadTo;

    @After
    public void cleanUp() throws IOException {
        if (server != null) {
            server.stop(0);
        }
        if (downloadTo != null) {
            Files.deleteIfExists(downloadTo);
        }
    }

    @Test
    public void testCompleteDownload_reportsCompletedWithCorrectContent() throws Exception {
        byte[] content = "this is a complete, correctly sized download".getBytes(StandardCharsets.UTF_8);
        server = startServer(exchange -> {
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.getResponseBody().close();
        });

        Result result = download();

        assertTrue("completed() must be called for a download that matches Content-Length",
                result.completed.get());
        assertFalse("error() must not be called for a successful download", result.error.get());
        assertEquals(content.length, result.totalBytes.get());
        assertArrayEquals(content, Files.readAllBytes(downloadTo));
    }

    @Test
    public void testTruncatedDownload_reportsErrorNotCompleted() throws Exception {
        byte[] fullContent = "this response claims to be longer than what actually gets sent".getBytes(StandardCharsets.UTF_8);
        byte[] partialContent = new byte[10];
        System.arraycopy(fullContent, 0, partialContent, 0, partialContent.length);

        server = startServer(exchange -> {
            // Declare the full length, but only write (and then abruptly
            // close the connection on) a fraction of it - simulates a
            // dropped connection / truncated download.
            exchange.sendResponseHeaders(200, fullContent.length);
            exchange.getResponseBody().write(partialContent);
            exchange.close();
        });

        Result result = download();

        assertFalse("completed() must NOT be called for a truncated download - "
                        + "that would mean a corrupt installer gets executed",
                result.completed.get());
        assertTrue("error() must be called for a truncated download", result.error.get());
    }

    @Test
    public void testChunkedDownload_unknownContentLengthIsNotTreatedAsTruncated() throws Exception {
        byte[] content = "chunked response with no Content-Length header".getBytes(StandardCharsets.UTF_8);
        server = startServer(exchange -> {
            // sendResponseHeaders with length 0 makes the JDK HttpServer use
            // chunked transfer-encoding, i.e. no Content-Length is sent and
            // URLConnection#getContentLengthLong() will return -1.
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(content);
            exchange.getResponseBody().close();
        });

        Result result = download();

        assertTrue("a fully-delivered chunked response (unknown length) must still be "
                        + "reported as completed, not rejected as truncated",
                result.completed.get());
        assertFalse(result.error.get());
        assertArrayEquals(content, Files.readAllBytes(downloadTo));
    }

    @Test
    public void testIsHttps() {
        assertTrue(UpdateDialog.isHttps(URI.create("https://github.com/example/example/releases/download/v1/setup.exe")));
        assertTrue("scheme check must be case-insensitive",
                UpdateDialog.isHttps(URI.create("HTTPS://github.com/example/setup.exe")));
        assertFalse(UpdateDialog.isHttps(URI.create("http://github.com/example/setup.exe")));
        assertFalse(UpdateDialog.isHttps(URI.create("file:///etc/passwd")));
        assertFalse("a URI with no scheme must not be treated as https",
                UpdateDialog.isHttps(URI.create("//github.com/example/setup.exe")));
    }

    //=== Helpers ===

    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }

    private static HttpServer startServer(Handler handler) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        s.start();
        return s;
    }

    private Result download() throws Exception {
        downloadTo = Files.createTempFile("FileDownloaderTest", ".tmp");
        URL from = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/").toURL();

        CountDownLatch done = new CountDownLatch(1);
        Result result = new Result();
        FileDownloader downloader = new FileDownloader(from, downloadTo, new FileDownloaderListener() {
            @Override
            public void completed(long totalBytes, long contentLength) {
                result.completed.set(true);
                result.totalBytes.set(totalBytes);
                done.countDown();
            }

            @Override
            public void error(IOException ex) {
                result.error.set(true);
                result.exception.set(ex);
                done.countDown();
            }

            @Override
            public void progress(long totalBytes, long contentLength) {
            }

            @Override
            public void cancelled(long totalBytes, long contentLength) {
                done.countDown();
            }
        });
        // Run on the test thread directly (FileDownloader#run() is
        // synchronous internally); no need for startAsync()/startAsyncDaemon().
        downloader.run();

        assertTrue("download did not finish", done.await(5, TimeUnit.SECONDS));
        if (result.error.get()) {
            assertNotNull(result.exception.get());
        }
        return result;
    }

    private static class Result {
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicBoolean error = new AtomicBoolean();
        final AtomicLong totalBytes = new AtomicLong();
        final AtomicReference<IOException> exception = new AtomicReference<>();
    }

}
