package org.projectfloodlight.db.data.syncmem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.junit.After;
import org.junit.Test;
import org.projectfloodlight.db.data.syncmem.HttpServer;
import org.projectfloodlight.db.data.syncmem.ServerReceiveHandler;
import org.projectfloodlight.db.data.syncmem.ServerReceiveHandler.Response;

import com.google.common.base.Charsets;


public class HttpServerTest {
    private HttpServer server;
    private URI uri;

    public void startServer(ServerReceiveHandler handler) {
        int port = ServerPortUtils.findFreePort(8080);
        uri = URI.create("http://localhost:"+port+"/config");
        server = new HttpServer(port, handler);
        server.start();
    }

    @Test
    public void testStart() {
        startServer(null);
    }

    @Test
    public void testOneRequest() throws Exception {
        ServerReceiveHandler handler = EasyMock.createMock(ServerReceiveHandler.class);
        Capture<ChannelBuffer> bufferCapture = new Capture<ChannelBuffer>();
        EasyMock.expect(handler.messageReceived(EasyMock.capture(bufferCapture), EasyMock.<SocketAddress>anyObject())).andReturn(new Response(HttpResponseStatus.OK, ChannelBuffers.copiedBuffer("TestResponse", Charsets.UTF_8))).once();
        EasyMock.replay(handler);
        startServer(handler);

        clientRequest("TestRequest", "TestResponse");
        assertTrue(bufferCapture.hasCaptured());
        assertEquals("TestRequest", bufferCapture.getValue().toString(Charsets.UTF_8));
    }

    private void clientRequest(String request, String response) throws MalformedURLException, IOException, ProtocolException {
        URL url = uri.toURL();
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        httpCon.setDoOutput(true);
        httpCon.setRequestMethod("PUT");
        OutputStreamWriter out = new OutputStreamWriter(
            httpCon.getOutputStream());
        out.write(request);
        out.close();
        httpCon.connect();
        
        try (InputStream stream = httpCon.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            assertEquals(response, reader.readLine());
        }
    }

    @Test
    public void testTwoRequestsKeepAlive() throws IOException {
        testTwoRequests(true);
     }

    @Test
    public void testTwoRequestsNoKeepAlive() throws IOException {
        testTwoRequests(false);
     }

    public void testTwoRequests(boolean keepalive) throws IOException {
        System.setProperty("http.keepAlive", Boolean.toString(keepalive));

        TestServerReceiveHandler handler = new TestServerReceiveHandler();
        startServer(handler);

        clientRequest("TestRequest1", "TestResponse1");
        clientRequest("TestRequest2", "TestResponse2");
        assertEquals(2, handler.numRequests);
    }

    static class TestServerReceiveHandler implements ServerReceiveHandler {
        private int numRequests = 0;

        @Override
        public Response messageReceived(ChannelBuffer content, SocketAddress address) {
            int curRequest = ++numRequests;
            assertEquals("TestRequest"+curRequest, content.toString(Charsets.UTF_8));
            return new Response(HttpResponseStatus.OK, ChannelBuffers.copiedBuffer("TestResponse"+curRequest, Charsets.UTF_8));
        }
    }

    @After
    public void shutdownServer() {
        if(server != null)
            server.shutdown();
        server = null;
    }

}
