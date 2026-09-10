
package chatty.util;

import org.junit.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for doRequests()'s re-entrancy guard: it used to be a
 * non-atomic check-then-act on a plain volatile boolean with no cleanup on
 * exception, so (a) two overlapping calls could both pass the guard and
 * fire duplicate requests, and (b) a throwing requester permanently wedged
 * the "Ignored doRequests" state forever after.
 */
public class CachedBulkManagerTest {

    @Test
    public void testOverlappingCalls_secondCallIsRejectedWhileFirstInProgress() throws InterruptedException {
        AtomicInteger requestCalls = new AtomicInteger();
        CountDownLatch requesterEntered = new CountDownLatch(1);
        CountDownLatch releaseRequester = new CountDownLatch(1);

        CachedBulkManager<String, String> manager = new CachedBulkManager<>((mgr, asap, normal, backlog) -> {
            requestCalls.incrementAndGet();
            requesterEntered.countDown();
            try {
                // Block here to simulate an in-progress request, giving a
                // second, overlapping doRequests() call a window to run.
                releaseRequester.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            mgr.setNotFound(asap);
        }, CachedBulkManager.DAEMON);

        // NONE (not ASAP): query() only auto-triggers doRequests() for
        // ASAP queries, so this registers the key without immediately
        // (and synchronously, on this thread) invoking the requester -
        // doRequests() is called explicitly below instead, to control
        // exactly when and on which thread each call happens.
        manager.query(result -> { }, CachedBulkManager.NONE, "key1");

        Thread firstCall = new Thread(manager::doRequests);
        firstCall.start();

        assertTrue("first call must have entered the requester", requesterEntered.await(5, TimeUnit.SECONDS));

        // Overlapping call while the first is still blocked inside the
        // requester: must be rejected (not call the requester again).
        manager.doRequests();
        assertEquals("the overlapping call must not have invoked the requester a second time",
                1, requestCalls.get());

        releaseRequester.countDown();
        firstCall.join(5000);
    }

    @Test
    public void testThrowingRequester_doesNotPermanentlyWedgeFutureCalls() {
        AtomicInteger requestCalls = new AtomicInteger();

        CachedBulkManager<String, String> manager = new CachedBulkManager<>((mgr, asap, normal, backlog) -> {
            requestCalls.incrementAndGet();
            mgr.setNotFound(asap);
            throw new RuntimeException("simulated requester failure");
        }, CachedBulkManager.DAEMON);

        // query() with ASAP triggers doRequests() internally and
        // synchronously, so the requester's exception propagates out of
        // query() itself here - not out of a separate doRequests() call.
        queryIgnoringException(manager, "key1");
        queryIgnoringException(manager, "key2");

        assertEquals("a later call must still be able to reach the requester "
                        + "(the guard must not be permanently stuck after an exception)",
                2, requestCalls.get());
    }

    /**
     * The requester's own exception is expected to propagate out of this
     * call; what matters for this test is only that it doesn't leave the
     * re-entrancy guard stuck for future calls.
     */
    private static void queryIgnoringException(CachedBulkManager<String, String> manager, String key) {
        try {
            manager.query(result -> { }, CachedBulkManager.ASAP, key);
        } catch (RuntimeException expected) {
        }
    }

}
