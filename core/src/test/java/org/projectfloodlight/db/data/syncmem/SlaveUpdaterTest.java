package org.projectfloodlight.db.data.syncmem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.db.data.syncmem.HttpClient;
import org.projectfloodlight.db.data.syncmem.HttpClientReceiver;
import org.projectfloodlight.db.data.syncmem.SlaveId;
import org.projectfloodlight.db.data.syncmem.SlaveUpdater;
import org.projectfloodlight.db.data.syncmem.SyncContent;

import com.google.common.base.Charsets;
import com.google.common.base.Ticker;

public class SlaveUpdaterTest {
    static class MockHttpClient implements HttpClient {

        HttpClientReceiver clientReceiver;

        @Override
        public void request(HttpMethod method, String contentType, byte[] data,
                Map<String, String> cookies) {
            // noop

        }

        @Override
        public void setClientReceiver(HttpClientReceiver clientReceiver) {
            this.clientReceiver = clientReceiver;
        }

    }

    private MockHttpClient client;

    @Test
    public void testUpdateOk() throws Exception {
        DumbContent content = new DumbContent("hallo");

        SlaveUpdater s = new SlaveUpdater(new SlaveId("1"), client);
        assertEquals(s.getState(), SlaveUpdater.State.IDLE);
        s.setContent(content);
        assertTrue(s.needsUpdate());
        s.update();
        assertEquals(s.getState(), SlaveUpdater.State.UPDATING);
        assertTrue(s.needsUpdate());

        client.clientReceiver.okReceived(new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK));
        assertFalse(s.needsUpdate());
        assertTrue(s.getWaitTime() > 0 );
    }

    static class FakeTicker extends Ticker {

        long time = System.nanoTime();

        public void passMs(long ms) {
            time += ms * 1000 * 1000;
        }

        @Override
        public long read() {
            return time;
        }
    }

    @Test
    public void testUpdateFailExceptionAndRetry() throws Exception {
        DumbContent content = new DumbContent("hallo");

        SlaveUpdater s = new SlaveUpdater(new SlaveId("1"), client);
        FakeTicker ticker = new FakeTicker();
        s.setTicker(ticker);
        assertEquals(s.getState(), SlaveUpdater.State.IDLE);
        s.setContent(content);
        assertTrue(s.needsUpdate());
        s.update();
        assertEquals(s.getState(), SlaveUpdater.State.UPDATING);
        assertTrue(s.needsUpdate());

        client.clientReceiver.exceptionCaught(new RuntimeException("foo"));

        assertTrue(s.needsUpdate());
        assertTrue(s.getWaitTime() < SlaveUpdater.MAX_RETRY_INTERVAL );
        assertEquals(SlaveUpdater.State.ERROR, s.getState());
        // update while in error state is a noop
        s.update();
        assertEquals(SlaveUpdater.State.ERROR, s.getState());

        ticker.passMs(5000);
        assertTrue(s.getWaitTime() == 0);
        assertEquals(SlaveUpdater.State.IDLE, s.getState());

        client.clientReceiver.okReceived(new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK));
        assertFalse(s.needsUpdate());
        assertTrue(s.getWaitTime() > 0 );
    }

    @Test
    public void testUpdateFail500() throws Exception {
        DumbContent content = new DumbContent("hallo");

        SlaveUpdater s = new SlaveUpdater(new SlaveId("1"), client);
        assertEquals(s.getState(), SlaveUpdater.State.IDLE);
        s.setContent(content);
        assertTrue(s.needsUpdate());
        s.update();
        assertEquals(s.getState(), SlaveUpdater.State.UPDATING);
        assertTrue(s.needsUpdate());

        client.clientReceiver.nonOkReceived(new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.INTERNAL_SERVER_ERROR));

        assertTrue(s.needsUpdate());
        assertTrue(s.getWaitTime() < SlaveUpdater.MAX_RETRY_INTERVAL );
        assertEquals(SlaveUpdater.State.ERROR, s.getState());
    }

    @Test
    public void testUpdateIgnored() throws Exception {
        DumbContent content = new DumbContent("hallo");

        SlaveUpdater s = new SlaveUpdater(new SlaveId("1"), client);
        FakeTicker ticker = new FakeTicker();
        s.setTicker(ticker);
        assertEquals(s.getState(), SlaveUpdater.State.IDLE);
        s.setContent(content);
        assertTrue(s.needsUpdate());
        s.update();
        assertEquals(s.getState(), SlaveUpdater.State.UPDATING);
        assertTrue(s.needsUpdate());

        client.clientReceiver.nonOkReceived(new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.SERVICE_UNAVAILABLE));

        assertTrue(s.needsUpdate());
        assertTrue(s.getWaitTime() < SlaveUpdater.MAX_RETRY_INTERVAL );
        assertEquals(SlaveUpdater.State.QUIESCENCE, s.getState());

        ticker.passMs(SlaveUpdater.QUIESCENCE_TIMEOUT);

        assertEquals(SlaveUpdater.State.IDLE, s.getState());
        assertTrue(s.needsUpdate());
        s.update();
        client.clientReceiver.okReceived(new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK));
        assertFalse(s.needsUpdate());
        assertTrue(s.getWaitTime() > 0 );
    }

    @Test
    public void testConcurrentUpdates() throws Exception {
        DumbContent content = new DumbContent("hallo");

        SlaveUpdater s = new SlaveUpdater(new SlaveId("1"), client);
        FakeTicker ticker = new FakeTicker();
        s.setTicker(ticker);

        assertEquals(s.getState(), SlaveUpdater.State.IDLE);
        s.setContent(content);
        assertTrue(s.needsUpdate());
        s.update();
        assertEquals(s.getState(), SlaveUpdater.State.UPDATING);
        assertTrue(s.needsUpdate());

        // while the update is running, somebody changes the content
        DumbContent updatedContent = new DumbContent("heho");
        s.setContent(updatedContent);

        client.clientReceiver.okReceived(new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK));

        assertTrue(s.needsUpdate());
        assertTrue(s.getWaitTime() < SlaveUpdater.MAX_RETRY_INTERVAL );
        assertEquals(SlaveUpdater.State.QUIESCENCE, s.getState());
        ticker.passMs(SlaveUpdater.QUIESCENCE_TIMEOUT);
        assertEquals(SlaveUpdater.State.IDLE, s.getState());

        // a 2nd update fixes the concurrent update
        s.update();
        client.clientReceiver.okReceived(new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK));
        assertFalse(s.needsUpdate());
        assertTrue(s.getWaitTime() > 0 );
    }

    @Before
    public void createMocks() {
        client = new MockHttpClient();
    }


}

class DumbContent implements SyncContent {
    private final String s;

    DumbContent(String s) {
        this.s = s;
    }

    @Override
    public String getContentType() {
        return "text/plain; charset = UTF-8";
    }

    @Override
    public byte[] getUpdate(SyncContent currentContent) {
        return s.getBytes(Charsets.UTF_8);
    }
}
