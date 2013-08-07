package org.projectfloodlight.db.auth.session;

import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;

import org.projectfloodlight.db.auth.AuditEvent;
import org.projectfloodlight.db.auth.BigDBUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.Hashing;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Session data bean. Currently hardwired to our specific requirements in BigDB.
 * Could be more generic.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
@SuppressFBWarnings(value="EQ_COMPARETO_USE_OBJECT_EQUALS",
        justification="This class has a natuaral ordering based on its id, but no semantic equality that's needed")
public class Session implements Comparable<Session> {

    // predefined AV pairs for audit events
    public final static String PRINCIPAL_AVPAIR = "User";
    public final static String TASK_AVPAIR = "task_id";

    public static final String CREATE_TYPE = "Session.Create";
    public static final String REMOVE_TYPE = "Session.Remove";

    protected static Logger logger = LoggerFactory.getLogger(Session.class);

    /**
     * monotonically increasing integer issued by the SessionManager, unique
     * within the context of a SessionManager
     */
    private final long id;

    /**
     * an opaque cookies issued by the SessionManager. Must contain enough
     * entropy as not to be guessable and not repeat
     */
    private final String cookie;

    /** BigDB user this session is associated with */
    private final BigDBUser user;

    /** ms timestamp when this session was created */
    private final long created;

    /** # times this session was touched */
    private final AtomicInteger touchCounter;

    /** ms timestamp when this session was created */
    private volatile long lastTouched;

    /** source (ip) address of the last access to this session */
    private volatile String lastAddress;

    private volatile SessionListener sessionListener;

    public Session(long id, String cookie, BigDBUser user) {
        this.id = id;
        this.cookie = cookie;

        this.user = user;
        this.created = this.lastTouched = System.currentTimeMillis();
        this.touchCounter = new AtomicInteger(1);
    }

    @JsonCreator
    public Session(@JsonProperty("id") long id, @JsonProperty("cookie") String cookie,
            @JsonProperty("user") BigDBUser user, @JsonProperty("created") long created,
            @JsonProperty("lastTouched") long lastTouched,
            @JsonProperty("touchCount") int touchCount) {
        this.id = id;
        this.cookie = cookie;
        this.user = user;
        this.created = created;
        this.lastTouched = lastTouched;
        this.touchCounter = new AtomicInteger(touchCount);
    }

    public long getId() {
        return id;
    }

    public String getCookie() {
        return cookie;
    }

    /** return an opaque task ID based on the cookie
     * that uniquely identifies this session
     * (useful for accounting, but does not reveal the cookie)
     */
    public static String getTaskId(String cookie) {
        StringBuilder b = new StringBuilder();
        b.append("Session@");
        b.append(Hashing.sha256().hashString(cookie).toString().substring(0, 8));
        return b.toString();
    }

    /** update the touch counters of this session */
    public void touch() {
        this.lastTouched = System.currentTimeMillis();
        touchCounter.incrementAndGet();
        fireNotifyListener();
    }

    public BigDBUser getUser() {
        return user;
    }

    public long getCreated() {
        return created;
    }

    public int getTouchCount() {
        return touchCounter.get();
    }

    public long getLastTouched() {
        return lastTouched;
    }

    public String getLastAddress() {
        return lastAddress;
    }

    public void setLastAddress(String lastAddress) {
        if (!Objects.equal(this.lastAddress, lastAddress)) {
            this.lastAddress = lastAddress;
            fireNotifyListener();
        }
    }

    /** natural ordering of sessions: numberically by id */
    @Override
    public int compareTo(Session o) {
        if (this.id < o.id)
            return -1;
        else if (this.id == o.id)
            return 0;
        else
            return 1;
    }

    private void fireNotifyListener() {
        if (sessionListener != null)
            sessionListener.onSessionTouched(this);
    }

    @JsonIgnore
    public SessionListener getSessionListener() {
        return sessionListener;
    }

    @JsonIgnore
    public void setSessionListener(SessionListener sessionListener) {
        this.sessionListener = sessionListener;
    }

    public AuditEvent.Builder eventBuilder() {
        AuditEvent.Builder b = new AuditEvent.Builder();
        b.avPair(TASK_AVPAIR, getTaskId(cookie));
        b.avPair(PRINCIPAL_AVPAIR, getUser().getUser());
        return b;
    }

}
