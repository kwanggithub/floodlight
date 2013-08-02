package net.bigdb.data.syncmem;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;

import javax.management.JMException;
import javax.management.ObjectName;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.base.Strings;

interface HttpClientReceiver {
    void exceptionCaught(Throwable t);

    /** inform the client about a non-200 http response.
     *  @return whether to close the connection
     */
    boolean nonOkReceived(HttpResponse response);

    /** inform the client about a 200 OK http response */
    void okReceived(HttpResponse response);
}

public class HttpClientImpl implements HttpClient, HttpClientImplMBean {
    private final static Logger logger = LoggerFactory.getLogger(HttpClientImpl.class);

    private final ClientBootstrap bootstrap;
    private final DefaultChannelGroup allChannels;
    private final URI uri;
    private Channel requestChannel;
    volatile State state;
    private HttpClientReceiver clientReceiver;

    private final HttpClientStats stats;

    enum State {
        IDLE, CONNECTING, HANDLING_REQUEST, CONNECTED_IDLE;
    }

    HttpClientImpl(ClientBootstrap bootstrap, DefaultChannelGroup channelGroup, URI uri, HttpClientStats stats) {
        this.bootstrap = bootstrap;
        this.allChannels = channelGroup;
        this.uri = uri;
        this.stats = stats;
        this.state = State.IDLE;

        try {
            ObjectName objectName = new ObjectName("net.bigdb:type=HttpClientImpl,name="+this.uri.getHost() + "/" + this.uri.getPath());
            logger.info("Registering as MBean "+objectName);
            ManagementFactory.getPlatformMBeanServer().registerMBean(new JMXStats(), objectName);
        } catch (JMException e) {
            logger.debug("Error exposing MBean for HttpClientImpl");
            if(logger.isTraceEnabled())
                logger.trace("Stacktrace: ",e);
        }
    }

    @Override
    public void setClientReceiver(HttpClientReceiver clientReceiver) {
        this.clientReceiver = clientReceiver;
    }

    /*
     * (non-Javadoc)
     * @see
     * net.bigdb.data.persistmem.IHttpClient#request(org.jboss.netty.handler
     * .codec.http.HttpMethod, byte[], java.util.Map)
     */
    @Override
    public void request(HttpMethod method, String contentType, byte[] data,
            Map<String, String> cookies) {
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        String host = uri.getHost() == null ? "localhost" : uri.getHost();

        if (!scheme.equals("http")) {
            throw new IllegalArgumentException("Scheme " + scheme + " not supported");
        }

        HttpRequest request =
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, method, uri.getPath());

        request.setHeader(HttpHeaders.Names.HOST, host);
        request.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        if (!Strings.isNullOrEmpty(contentType)) {
            request.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType);
        }

        if (cookies != null) {
            CookieEncoder httpCookieEncoder = new CookieEncoder(false);
            for (Map.Entry<String, String> m : cookies.entrySet()) {
                httpCookieEncoder.addCookie(m.getKey(), m.getValue());
                request.setHeader(HttpHeaders.Names.COOKIE, httpCookieEncoder.encode());
            }
        }
        if (data != null) {
            request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, data.length);
            request.setContent(ChannelBuffers.copiedBuffer(data));
        }

        stats.incRequests();

        if(this.state == State.CONNECTED_IDLE && this.requestChannel.isConnected()) {
            stats.incReusedConnections();
            this.state = State.HANDLING_REQUEST;
            this.requestChannel.write(request).addListener(new WriteCompleteListener(request));
        } else {
            this.requestChannel = createRequest(request);
        }

    }

    private Channel createRequest(HttpRequest request) {
        int port = uri.getPort() == -1 ? 80 : uri.getPort();
        ChannelFuture future =
                bootstrap.connect(new InetSocketAddress(request
                        .getHeader(HttpHeaders.Names.HOST), port));
        state = State.CONNECTING;
        future.addListener(new ConnectOk(request));
        Channel channel = future.getChannel();
        allChannels.add(channel);
        channel.getPipeline().addLast("last", new HttpUpstreamHandler());
        return channel;
    }

    class ConnectOk implements ChannelFutureListener {
        private HttpRequest request = null;

        ConnectOk(HttpRequest req) {
            this.request = req;
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            if (future.isSuccess()) {
                stats.incConnections();
            } else {
                stats.connectionError(future.getCause());
                logger.warn("Error on connection: " + future.getCause());
                return;
            }
            Channel channel = future.getChannel();
            channel.write(request).addListener(new WriteCompleteListener(request));
            HttpClientImpl.this.state = State.HANDLING_REQUEST;
        }
    }

    class WriteCompleteListener implements ChannelFutureListener {
        private final HttpRequest request;

        public WriteCompleteListener(HttpRequest request) {
            this.request = request;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if(future.isSuccess()) {
                stats.addBytesSend(HttpHeaders.getContentLength(request));
            } else {
                stats.connectionError(future.getCause());
            }
        }

    }

    class HttpUpstreamHandler extends SimpleChannelUpstreamHandler {
        public HttpUpstreamHandler() {
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            super.channelClosed(ctx, e);

            if(logger.isDebugEnabled())
                logger.debug("Channel closed "+e.toString());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent event)
                throws Exception {
            Throwable t = event.getCause();
            logger.warn(
                    "Exception caught during sync of client " + HttpClientImpl.this+ ": " + t.getMessage(),
                    t);
            clientReceiver.exceptionCaught(t);
            ctx.getChannel().close();
            HttpClientImpl.this.state = State.IDLE;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            HttpResponse response = (HttpResponse) e.getMessage();

            if (Objects.equal(response.getStatus(),HttpResponseStatus.OK)) {
                stats.addBytesReceived(HttpHeaders.getContentLength(response));
                clientReceiver.okReceived(response);
            } else {
                boolean close = clientReceiver.nonOkReceived(response);

                if(close)
                    ctx.getChannel().close();
            }
            stats.httpResponseStatus(response.getStatus());
            HttpClientImpl.this.state = State.CONNECTED_IDLE;
        }
    }

    @Override
    public String toString() {
        return "HttpClientImpl [uri=" + uri + ", state=" + state + "]";
    }

    /* (non-Javadoc)
     * @see net.bigdb.data.syncmem.HttpClientImplMBean#getState()
     */
    @Override
    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    /* (non-Javadoc)
     * @see net.bigdb.data.syncmem.HttpClientImplMBean#getUri()
     */
    @Override
    public URI getUri() {
        return uri;
    }

    /* (non-Javadoc)
     * @see net.bigdb.data.syncmem.HttpClientImplMBean#getStats()
     */
    @Override
    public HttpClientStats getStats() {
        return stats;
    }

    class JMXStats implements JMXStatsMBean {
        @Override
        public URI getUri() {
            return uri;
        }

        @Override
        public String getState() {
           return state.toString();
        }

        @Override
        public long getBytesSent() {
            return stats.getBytesSent();
        }

        @Override
        public long getBytesReceived() {
            return stats.getBytesReceived();
        }

        @Override
        public int getNumConnections() {
            return stats.getNumConnections();
        }

        @Override
        public int getNumReusedConnections() {
            return stats.getNumReusedConnections();
        }

        @Override
        public int getNumRequests() {
            return stats.getNumRequests();
        }

        @Override
        public int getNumExceptions() {
            return stats.getNumExceptions();
        }

        @Override
        public Map<Integer, Integer> getReponseCodeStat() {
            return stats.getReponseCodeStat();
        }

        @Override
        public Throwable getLastException() {
            return stats.getLastException();
        }
    }

    public interface JMXStatsMBean {

        URI getUri();

        int getNumReusedConnections();

        int getNumRequests();

        String getState();

        long getBytesSent();

        long getBytesReceived();

        int getNumConnections();

        int getNumExceptions();

        Map<Integer, Integer> getReponseCodeStat();

        Throwable getLastException();

    }

}
