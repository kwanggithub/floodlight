package org.projectfloodlight.db.data.syncmem;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableMap;

public class SlaveUpdater {
    private final static Logger logger = LoggerFactory.getLogger(SlaveUpdater.class);

    // MAX RETRY INTERVAL IN NANOSECONDS
    public static final long MAX_RETRY_INTERVAL = TimeUnit.SECONDS.toNanos(10);
    public static final long UPDATE_TIMEOUT = TimeUnit.SECONDS.toNanos(2);
    public static final long QUIESCENCE_TIMEOUT = TimeUnit.MILLISECONDS.toNanos(250);

    private final static Random random = new Random();

    private final SlaveId id;

    enum State {
        IDLE, UPDATING, ERROR, QUIESCENCE
    }

    private State state;
    private final HttpClient client;
    private SyncContent requestedContent;
    private SyncContent pendingContent;
    private SyncContent currentContent;

    private int numIgnored = 0;

    private long lastUpdateStarted;
    private long lastUpdateCompleted;
    // binary backoff
    private long lastError;
    private long errorTimeOut;
    private Ticker ticker = Ticker.systemTicker();

    public SlaveUpdater(SlaveId id, HttpClient client) {
        this.id = id;
        state = State.IDLE;
        this.client = client;
        client.setClientReceiver(new SlaveHttpClientReceiver());
        this.pendingContent = this.currentContent = null;
    }

    synchronized void setTicker(Ticker ticker) {
        this.ticker = ticker;
    }

    public synchronized void setContent(SyncContent content) {
        this.requestedContent = content;
    }

    public synchronized void update() {
        if (getState() == State.IDLE) {
            if (needsUpdate()) {
                if(logger.isDebugEnabled()) {
                    logger.debug("Dispatching slave update for content "+pendingContent + " to "+client);
                }
                this.state = State.UPDATING;
                this.pendingContent = this.requestedContent;
                this.lastUpdateStarted = ticker.read();
                client.request(HttpMethod.PUT, this.pendingContent.getContentType(), this.pendingContent.getUpdate(this.currentContent),
                        ImmutableMap.<String, String> of());
            }
        }
    }

    public SyncContent getCurrentContent() {
        return currentContent;
    }

    public synchronized boolean needsUpdate() {
        return !Objects.equal(this.requestedContent, this.currentContent);
    }

    public synchronized long getWaitTime() {
        if (getState() == State.ERROR)
            return Math.max(0, (lastError + errorTimeOut) - ticker.read());
        if (getState() == State.UPDATING)
            return Math.max(0, (lastUpdateStarted + UPDATE_TIMEOUT) - ticker.read());
        if (getState() == State.QUIESCENCE)
            return Math.max(0, (lastUpdateCompleted + QUIESCENCE_TIMEOUT) - ticker.read());
        else
            return needsUpdate() ? 0 : MAX_RETRY_INTERVAL;
    }

    public synchronized State getState() {
        if (state == State.ERROR &&  (lastError + errorTimeOut) - ticker.read()  < 0) {
            if(logger.isDebugEnabled()) {
                logger.debug("SlaveUpdater "+id +" reverting from error to idle");
            }
            state = State.IDLE;
        } else if (state == State.UPDATING && (lastUpdateStarted + UPDATE_TIMEOUT ) - ticker.read() < 0 ) {
            logger.warn("Sync Client "+client + " - update timed out");
            state = State.IDLE;
        } else if (state == State.QUIESCENCE && (lastUpdateCompleted + QUIESCENCE_TIMEOUT ) - ticker.read() < 0 ) {
            state = State.IDLE;
        }
        return state;
    }

    class SlaveHttpClientReceiver implements HttpClientReceiver {
        @Override
        public void exceptionCaught(Throwable t) {
            if(t instanceof IOException) {
                logger.warn("IO error during sync of slave {}: {} ({}) ",
                            new Object[] { id, t.getMessage(), t.getClass().getSimpleName() });

                if(logger.isDebugEnabled()) {
                    logger.debug("Exception Stack trace: ", t);
                }
            } else {
                logger.warn("Exception caught during sync of slave {}: {} ({}) ", new Object[] {id, t.getMessage(), t.getClass().getSimpleName() }, t);
            }
            updateErrorState();
        }

        @Override
        public void okReceived(HttpResponse response) {
            updateSuccessState();
        }

        @Override
        public boolean nonOkReceived(HttpResponse response) {
            if(Objects.equal(response.getStatus(), HttpResponseStatus.SERVICE_UNAVAILABLE)) {
                logger.warn("Slave ignored our update - update of slave " + id);
                updateIgnoredState();
                return false;
            } else {
                logger.warn("Error State " + response.getStatus() + " for update of slave " + id);
                updateErrorState();
                return true;
            }
        }

    }

    synchronized int getNumIgnored() {
        return numIgnored;
    }

    public synchronized long getErrorTimeOut() {
        return errorTimeOut;
    }

    public synchronized void updateErrorState() {
        if (this.lastError > 0) {
            // previous attempt was an error - binary backoff
            errorTimeOut = Math.min(errorTimeOut * 2, MAX_RETRY_INTERVAL);
        } else {
            errorTimeOut = random.nextInt(200 * 1000 * 1000) + 100 * 1000 * 1000;
        }
        if(logger.isDebugEnabled()) {
            logger.debug("SlaveUpdater "+id +" error state: timeout " + errorTimeOut/(1000*1000));
        }

        this.lastError = ticker.read();
        this.state = State.ERROR;
    }

    /** update status in response to an ignored message from the client.
     *  Don't do the error back off (we expect the roles are going to converge soon).
     *  Go into quiescense and try again.
     */
    public synchronized void updateIgnoredState() {
        this.lastUpdateCompleted = ticker.read();
        state = State.QUIESCENCE;
        lastError = 0;
        numIgnored++;
    }

    public synchronized void updateSuccessState() {
        if(logger.isDebugEnabled()) {
            logger.debug("update Success - pending="+pendingContent + " to "+client);
        }

        this.lastUpdateCompleted = ticker.read();
        this.currentContent = pendingContent;
        state = State.QUIESCENCE;
        lastError = 0;
    }

    @Override
    public String toString() {
        return "SlaveUpdater [id=" + id + ", state=" + state + ", client=" + client
                + ", pendingContent=" + pendingContent + ", currentContent="
                + currentContent + ", lastError=" + lastError + ", errorTimeOut="
                + errorTimeOut + "]";
    }

}
