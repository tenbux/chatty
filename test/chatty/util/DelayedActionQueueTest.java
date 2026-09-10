
package chatty.util;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for the reader thread's resilience: previously a
 * RuntimeException from the listener was only guarded against
 * InterruptedException, so it killed the reader thread silently, after
 * which nothing ever consumed the queue again. The thread must survive a
 * bad item and keep processing later ones.
 */
public class DelayedActionQueueTest {

    @Test
    public void testListenerException_doesNotKillReaderThread() throws InterruptedException {
        List<String> processed = new CopyOnWriteArrayList<>();
        CountDownLatch secondItemProcessed = new CountDownLatch(1);

        DelayedActionQueue<String> queue = DelayedActionQueue.create(item -> {
            processed.add(item);
            if (item.equals("bad")) {
                throw new RuntimeException("simulated listener failure");
            }
            if (item.equals("after")) {
                secondItemProcessed.countDown();
            }
        }, 1 /* ms delay, keep the test fast */);

        queue.add("bad");
        queue.add("after");

        assertTrue("the item added after the throwing one must still be processed "
                        + "(reader thread must not have died)",
                secondItemProcessed.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("bad", "after"), processed);
    }

}
