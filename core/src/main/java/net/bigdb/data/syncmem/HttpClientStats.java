package net.bigdb.data.syncmem;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

class HttpClientStats {
    private final HttpClientStats parent;

    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong bytesReceived = new AtomicLong();
    private final AtomicInteger numRequests = new AtomicInteger();
    private final AtomicInteger numConnections = new AtomicInteger();
    private final AtomicInteger numReusedConnections = new AtomicInteger();
    private final AtomicInteger numExceptions = new AtomicInteger();
    private final ConcurrentMap<Integer, AtomicInteger> responseCodeStats = new ConcurrentHashMap<Integer, AtomicInteger>();

    private volatile Throwable lastException = null;

    public HttpClientStats() {
        this.parent = null;
    }

    private HttpClientStats(HttpClientStats parent) {
        this.parent = parent;
    }

    public long getBytesSent() {
        return bytesSent.get();
    }

    public long getBytesReceived() {
        return bytesReceived.get();
    }

    public int getNumConnections() {
        return numConnections.get();
    }

    public int getNumExceptions() {
        return numExceptions.get();
    }

    public int getNumReusedConnections() {
        return numReusedConnections.get();
    }

    public int getNumRequests() {
        return numRequests.get();
    }

    public Map<Integer, Integer> getReponseCodeStat() {
        Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
        for(Entry<Integer, AtomicInteger> entry : responseCodeStats.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().get());
        }
        return counts;
    }

    public Throwable getLastException() {
        return lastException;
    }

    public void setLastException(Throwable lastException) {
        this.lastException = lastException;
    }

    void incConnections() {
        if(parent != null)
            parent.incConnections();

        numConnections.incrementAndGet();
    }

    void incReusedConnections() {
        if(parent != null)
            parent.incReusedConnections();

        numReusedConnections.incrementAndGet();
    }

    void addBytesSend(long delta) {
        if(parent != null)
            parent.addBytesSend(delta);

        this.bytesSent.addAndGet(delta);
    }

    void addBytesReceived(long delta) {
        if(parent != null)
            parent.addBytesReceived(delta);

        this.bytesReceived.addAndGet(delta);
    }

    void connectionError(Throwable cause) {
        if(parent != null)
            parent.connectionError(cause);

        numExceptions.incrementAndGet();
        this.lastException = cause;
    }

    void httpResponseStatus(HttpResponseStatus status) {
        if(parent != null)
            parent.httpResponseStatus(status);

        AtomicInteger reqCounts = responseCodeStats.get(status.getCode());
        if(reqCounts == null) {
            AtomicInteger newReqCount = new AtomicInteger();
            AtomicInteger presentCount;
            presentCount = responseCodeStats.putIfAbsent(status.getCode(), newReqCount);
            reqCounts = (presentCount != null ) ? presentCount : newReqCount;
        }
        reqCounts.incrementAndGet();
    }

    public HttpClientStats createChildStats() {
        return new HttpClientStats(this);
    }

    public void incRequests() {
        if(parent != null)
            parent.incRequests();

        numRequests.incrementAndGet();
    }

}