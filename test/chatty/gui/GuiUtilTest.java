
package chatty.gui;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * Regression test for edtAndWait()'s bounded timeout: it used to call
 * SwingUtilities.invokeAndWait() with no timeout, so if the EDT was blocked
 * (or gone), the calling thread hung forever. This is notably reached from
 * the JVM shutdown hook on save, where hanging here would prevent the app
 * from ever fully exiting.
 */
public class GuiUtilTest {

    @Test
    public void testEdtAndWait_blockedEdt_returnsInsteadOfHangingForever() throws InterruptedException {
        CountDownLatch releaseEdt = new CountDownLatch(1);
        try {
            long start = System.currentTimeMillis();
            GuiUtil.edtAndWait(() -> {
                try {
                    // Simulates a blocked/stuck EDT. Bounded (rather than
                    // truly infinite) so the EDT recovers after this test,
                    // instead of leaking a permanently stuck thread that
                    // could affect other tests using Swing in the same run.
                    releaseEdt.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }, "test");
            long elapsed = System.currentTimeMillis() - start;

            assertTrue("edtAndWait() must give up and return once the "
                            + "bounded timeout elapses instead of waiting for "
                            + "the blocked runnable to actually finish "
                            + "(took " + elapsed + "ms)",
                    elapsed < 20_000);
        } finally {
            releaseEdt.countDown();
        }
    }

    @Test
    public void testEdtAndWait_normalRunnable_runsAndReturnsPromptly() throws InterruptedException {
        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean();
        long start = System.currentTimeMillis();
        GuiUtil.edtAndWait(() -> ran.set(true), "test");
        long elapsed = System.currentTimeMillis() - start;

        assertTrue("the runnable must actually have run", ran.get());
        assertTrue("a normal, fast runnable must not be delayed by the "
                        + "timeout mechanism (took " + elapsed + "ms)",
                elapsed < 5_000);
    }

}
