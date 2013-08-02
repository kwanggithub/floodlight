package net.bigdb.data.syncmem;

import java.net.URI;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;

public class HttpClientFactory {

    private final ClientBootstrap bootstrap;
    private final DefaultChannelGroup allChannels;
    private final HttpClientStats allStats;

    HttpClientFactory() {
        bootstrap =
                new ClientBootstrap(new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
        bootstrap.setOption("connectTimeoutMillis", 5000);
        bootstrap.setPipelineFactory(new HttpClientPipelineFactory());
        allChannels = new DefaultChannelGroup();
        allStats = new HttpClientStats();
    }

    public HttpClient getClient(URI uri) {
        return new HttpClientImpl(bootstrap, allChannels, uri, allStats.createChildStats());
    }

    public void closeAll() {
        allChannels.close();
    }

    public HttpClientStats getAllStats() {
        return allStats;
    }
}

class HttpClientPipelineFactory implements ChannelPipelineFactory {
    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast("decoder", new HttpResponseDecoder());
        pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
        pipeline.addLast("encoder", new HttpRequestEncoder());
        return pipeline;
    }
}
