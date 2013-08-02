package net.bigdb.data.syncmem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;

public class HttpClientTest {
    protected final static Logger logger = LoggerFactory.getLogger(HttpClientTest.class);

    private static RestServer restServer;
    private static int serverPort;

    @BeforeClass
    public static void startServer() throws Exception {
        serverPort = ServerPortUtils.findFreePort(8080);
        restServer = new RestServer(serverPort);
        restServer.start();
    }

    @AfterClass
    public static void shutdownServer() throws Exception {
        restServer.stop();
    }

    static class RestServer {
        private final Component component;
        private final int port;

        public RestServer(int port) {
            this.port = port;
            component = new Component();
            component.getServers().add(Protocol.HTTP, port);
            component.getDefaultHost().attach(new RestApplication());
        }

        public void start() throws Exception {
            logger.debug("Starting demo restlet server on port " + port);
            component.start();
        }

        public void stop() throws Exception {
            component.stop();
        }
    }

    static class RestApplication extends Application {
        protected static String lastData;
        protected static Method lastMethod;

        @Override
        public Restlet createInboundRoot() {
            return new Restlet() {
                @Override
                public void handle(Request request, Response response) {
                    updateLastData(request.getEntityAsText(), request.getMethod());
                }
            };
        }

        protected static void updateLastData(String lastData, Method lastMethod) {
            RestApplication.lastData = lastData;
            RestApplication.lastMethod = lastMethod;
        }
    }

    @Test
    public void testSanity() {
        assertTrue(true);
    }

    @Test
    public void testPut() throws Throwable {
        HttpClientFactory factory = new HttpClientFactory();
        HttpClient client =
                factory.getClient(URI.create("http://localhost:" + serverPort + "/config"));

        TestClientReceiver clientReceiver = new TestClientReceiver();
        client.setClientReceiver(clientReceiver);
        client.request(HttpMethod.PUT, "text/plain; charset=UTF-8",
                        "Hallo".getBytes(Charsets.UTF_8),
                        ImmutableMap.<String, String> of());

        clientReceiver.waitFor();
        assertEquals(Method.PUT, RestApplication.lastMethod);
        assertEquals("Hallo", RestApplication.lastData);
    }

    static class TestClientReceiver implements HttpClientReceiver {
        private static final long TIMEOUT = 500;
        private boolean done = false;
        private Throwable throwable = null;
        String result;

        @Override
        public synchronized void exceptionCaught(Throwable t) {
            logger.error("Exception caught: ", t);
            throwable = t;
            done = true;
            notifyAll();

        }

        @Override
        public synchronized void okReceived(HttpResponse response) {
            result = new String(response.getContent().array(), Charsets.UTF_8);
            done = true;
            notifyAll();
        }

        public String getResult() {
            return result;
        }

        @Override
        public synchronized boolean nonOkReceived(HttpResponse response) {
            logger.warn("State " + response.getStatus() + " for client ");
            throwable =
                    new IllegalStateException("Response status " + response.getStatus());
            done = true;
            notifyAll();
            return true;
        }

        public synchronized void waitFor() throws Throwable {
            long start = System.currentTimeMillis();
            while (!done) {
                long remaining = TIMEOUT - (System.currentTimeMillis() - start);
                if (remaining > 0)
                    wait(remaining);
                else
                    throw new IllegalStateException("Didn't finish within timeout "
                            + TIMEOUT);
            }

            if (throwable != null)
                throw throwable;
        }

    }

}
