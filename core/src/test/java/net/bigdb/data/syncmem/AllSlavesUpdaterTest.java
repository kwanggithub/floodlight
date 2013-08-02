package net.bigdb.data.syncmem;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

public class AllSlavesUpdaterTest {
    private final static Logger logger =
            (Logger) LoggerFactory.getLogger(AllSlavesUpdaterTest.class);

    @BeforeClass
    public static void initLog() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.TRACE);
    }

    private AllSlavesUpdater updater;

    static class DumbClientFactory extends HttpClientFactory {

        protected int numRequests;

        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();

        @Override
        public HttpClient getClient(final URI uri) {
            return new HttpClient() {
                private HttpClientReceiver clientReceiver;

                @Override
                public void request(HttpMethod method, String contentType,
                        byte[] data, Map<String, String> cookies) {
                    ex.schedule(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (DumbClientFactory.this) {
                                clientReceiver.okReceived(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
                                numRequests++;
                                DumbClientFactory.this.notifyAll();
                            }
                        }
                    }, 20, TimeUnit.MILLISECONDS);
                }

                @Override
                public String toString() {
                    return "DumbClient[url="+uri+"]";
                }

                @Override
                public void setClientReceiver(HttpClientReceiver clientReceiver) {
                    this.clientReceiver = clientReceiver;
                }
            };
        }


        public synchronized void waitForRequests(int wantedRequests, long timeoutMs) throws TimeoutException, InterruptedException {
            long start = System.currentTimeMillis();
            while(this.numRequests < wantedRequests) {
                long timeLeft = timeoutMs - (System.currentTimeMillis() - start);
                if(timeLeft < 0)
                    throw new TimeoutException("DumbClientFactory: timed out waiting for "+wantedRequests + " to arrive -- seen "+numRequests);
                logger.debug("waiting for "+timeLeft);
                this.wait(timeLeft);
            }
        }

        public synchronized int getNumRequests() {
            return this.numRequests;
        }
    }

    @Test
    public void testOneClient() throws InterruptedException, TimeoutException {
        DumbClientFactory factory = new DumbClientFactory();
        updater = new AllSlavesUpdater(factory);
        updater.start();
        updater.addSlave(new SlaveId("host1"), URI.create("host1/config"));
        updater.setContent(new DumbContent("hallo"));

        factory.waitForRequests(1, 500);
        assertEquals(1, factory.getNumRequests());
        updater.shutdown();
    }

    @Test
    public void testMultipleClient() throws InterruptedException, TimeoutException {
        DumbClientFactory factory = new DumbClientFactory();
        updater = new AllSlavesUpdater(factory);
        updater.start();

        Thread.sleep(50);
        for(int i=0; i < 10; i++) {
            updater.addSlave(new SlaveId("host"+i), URI.create("host"+i+"/config"));
        }
        updater.setContent(new DumbContent("hallo"));

        factory.waitForRequests(10, 500);
        assertEquals(10, factory.getNumRequests());
        updater.shutdown();
    }

    @Test
    public void testMultipleClientDelayed() throws InterruptedException, TimeoutException {
        DumbClientFactory factory = new DumbClientFactory();
        updater = new AllSlavesUpdater(factory);
        updater.start();

        updater.setContent(new DumbContent("hallo"));
        for(int i=0; i < 10; i++) {
            updater.addSlave(new SlaveId("host"+i), URI.create("host"+i+"/config"));
            Thread.sleep(25);
        }
        factory.waitForRequests(10, 2000);
        logger.info("Dispatching second update");
        updater.setContent(new DumbContent("hallo2"));

        factory.waitForRequests(20, 2000);
        assertEquals(20, factory.getNumRequests());
        updater.shutdown();
    }
}
