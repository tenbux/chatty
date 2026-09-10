
package chatty.util.jws;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for the reader/writer thread resilience in
 * startConsumer(): previously only their respective expected exceptions
 * (InterruptedException / WebsocketNotConnectedException) were caught, so
 * any other exception from message dispatch or sending killed the thread
 * silently while the socket could still look open - nothing was ever
 * processed from the queue again.
 *
 * Drives the real reader thread by injecting directly into its internal
 * "received" queue via reflection (the only way to do this without a real
 * WebSocket connection, since startConsumer() itself doesn't connect -
 * only init()/connect() do).
 */
public class JWSClientTest {

    private JWSClient client;

    @After
    public void cleanUp() {
        if (client != null) {
            // Reader/writer threads loop forever otherwise; disconnect()
            // safely no-ops the actual socket close since it was never
            // connected (verified elsewhere: null readerThread/writerThread
            // fields aren't touched by startConsumer()-only usage since
            // those ARE set by startConsumer(), so interrupt() here does
            // apply to them) but still interrupts both loops via
            // JWSClient's own shutdown path.
            client.disconnect();
        }
    }

    @Test
    public void testReaderThread_survivesHandlerException() throws Exception {
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch secondMessageProcessed = new CountDownLatch(1);

        client = new JWSClient(URI.create("wss://test.invalid/"), new MessageHandler() {
            @Override
            public void handleReceived(String text) {
                received.add(text);
                if (text.equals("bad")) {
                    throw new RuntimeException("simulated handler failure");
                }
                if (text.equals("good")) {
                    secondMessageProcessed.countDown();
                }
            }

            @Override
            public void handleSent(String text) {
            }

            @Override
            public void handleConnect(JWSClient c) {
            }

            @Override
            public void handleDisconnect(int code) {
            }
        });

        // Start only the consumer threads, without connecting (init() would
        // attempt a real WebSocket connect via c.connect()).
        client.startConsumer();

        BlockingQueue<JWSClient.Received> receivedQueue = getReceivedQueue(client);
        receivedQueue.put(new JWSClient.Received(JWSClient.Received.Type.MESSAGE, "bad", 0));
        receivedQueue.put(new JWSClient.Received(JWSClient.Received.Type.MESSAGE, "good", 0));

        assertTrue("the message added after the throwing one must still be "
                        + "processed (reader thread must not have died)",
                secondMessageProcessed.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("bad", "good"), received);
    }

    @SuppressWarnings("unchecked")
    private static BlockingQueue<JWSClient.Received> getReceivedQueue(JWSClient client) throws Exception {
        Field f = JWSClient.class.getDeclaredField("received");
        f.setAccessible(true);
        return (BlockingQueue<JWSClient.Received>) f.get(client);
    }

}
