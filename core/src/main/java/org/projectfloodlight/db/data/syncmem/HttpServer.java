package org.projectfloodlight.db.data.syncmem;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.projectfloodlight.db.data.syncmem.ServerReceiveHandler.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;


interface ServerReceiveHandler {
    class Response {
        private final HttpResponseStatus status;
        private final ChannelBuffer content;

        Response(HttpResponseStatus status, ChannelBuffer content) {
            this.status = status;
            this.content = content;
        }

        public HttpResponseStatus getStatus() {
            return status;
        }

        public ChannelBuffer getContent() {
            return content;
        }

    }

    Response messageReceived(ChannelBuffer content, SocketAddress socketAddress) throws Exception;
}

public class HttpServer {
    private final static Logger logger = LoggerFactory.getLogger(HttpServer.class);

    private final int port;
    private final ServerReceiveHandler receiveHandler;
    private final ChannelGroup allChannels = new DefaultChannelGroup("syncmem http server");
    private final ChannelFactory channelFactory;

    public HttpServer(int port, ServerReceiveHandler handler) {
        this.port = port;
        this.receiveHandler = handler;

        this.channelFactory = new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
    }

    public void start() {
        // Configure the server.
        ServerBootstrap bootstrap =
                new ServerBootstrap(channelFactory);

        // Set up the event pipeline factory.
        bootstrap.setPipelineFactory(new HttpServerPipelineFactory());

        // Bind and start to accept incoming connections.
        Channel serverChannel = bootstrap.bind(new InetSocketAddress(port));
        allChannels.add(serverChannel);
    }

    class HttpServerPipelineFactory implements ChannelPipelineFactory {
        @Override
        public ChannelPipeline getPipeline() throws Exception {
            // Create a default pipeline implementation.
            ChannelPipeline pipeline = Channels.pipeline();

            // Uncomment the following line if you want HTTPS
            // SSLEngine engine =
            // SecureChatSslContextFactory.getServerContext().createSSLEngine();
            // engine.setUseClientMode(false);
            // pipeline.addLast("ssl", new SslHandler(engine));

            pipeline.addLast("decoder", new HttpRequestDecoder());
            pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
            pipeline.addLast("encoder", new HttpResponseEncoder());
            pipeline.addLast("deflater", new HttpContentCompressor());
            pipeline.addLast("handler", new HttpServerHandler());
            return pipeline;
        }
    }

    class HttpServerHandler extends SimpleChannelUpstreamHandler {

        @Override
        public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            allChannels.add(e.getChannel());
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            HttpRequest request = (HttpRequest) e.getMessage();
            if (HttpHeaders.is100ContinueExpected(request)) {
                send100Continue(e);
                return;
            }

            if (!Objects.equal(request.getMethod(), HttpMethod.PUT)) {
                send405MethodNotAllowed(request.getMethod(), e);
                return;
            }

            if (!Objects.equal("/config", request.getUri())) {
                send404NotFound(request.getUri(), e);
                return;
            }

            if (request.isChunked()) {
                throw new IllegalStateException(
                        "Chunked transfer should have been handled by the HttpChunkAggreator");
            }

            Response response= receiveHandler.messageReceived(request.getContent(), e.getRemoteAddress());
            sendResponse(e, response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            logger.warn("Exception caught in syncmem http server: "+e.getCause().getMessage(), e.getCause());
            // send500InternalServerError(ctx, e.getCause().getMessage());
            ctx.getChannel().close();
        }

        private void sendResponse(MessageEvent e, Response handlerResponse) {
            // Decide whether to close the connection or not.
            HttpRequest request = (HttpRequest) e.getMessage();
            boolean keepAlive = HttpHeaders.isKeepAlive(request);

            // Build the response object.
            HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, handlerResponse.getStatus());
            httpResponse.setContent(handlerResponse.getContent());
            httpResponse.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");

            if (keepAlive) {
                // Add 'Content-Length' header only for a keep-alive connection.
                httpResponse.setHeader(HttpHeaders.Names.CONTENT_LENGTH, httpResponse.getContent().readableBytes());
                // Add keep alive header as per:
                // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
                httpResponse.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
            }

            // Write the response.
            ChannelFuture future = e.getChannel().write(httpResponse);

            // Close the non-keep-alive connection after the write operation is done.
            if (!keepAlive) {
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }

        private void send100Continue(MessageEvent e) {
            HttpResponse response =
                    new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                            HttpResponseStatus.CONTINUE);
            e.getChannel().write(response);
        }

        private void send405MethodNotAllowed(HttpMethod method, MessageEvent e) {
            logger.warn("Unallowed method " + method + " in request " + e.getMessage());
            HttpResponse response =
                    new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                            HttpResponseStatus.METHOD_NOT_ALLOWED);
            e.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
        }

        private void send404NotFound(String uri, MessageEvent e) {
            logger.warn("Resource not found " + uri);
            HttpResponse response =
                    new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                            HttpResponseStatus.NOT_FOUND);
            e.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
        }

        public void send500InternalServerError(ChannelHandlerContext ctx, String msg) {
            HttpResponse response =
                    new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                            HttpResponseStatus.INTERNAL_SERVER_ERROR);
            response.setContent(ChannelBuffers.copiedBuffer(msg, Charsets.UTF_8));
            ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    public void shutdown() {
        ChannelGroupFuture future = allChannels.close();
        future.awaitUninterruptibly();
        channelFactory.releaseExternalResources();

    }

}
