package net.bigdb.data.syncmem;

import java.util.Map;

import org.jboss.netty.handler.codec.http.HttpMethod;

public interface HttpClient {

    public abstract void request(HttpMethod method, String contentType, byte[] data,
            Map<String, String> cookies);

    public void setClientReceiver(HttpClientReceiver clientReceiver);
}
