package net.bigdb.auth.session;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import net.bigdb.auth.BigDBUser;
import net.bigdb.auth.CreateAuthComponent;
import net.bigdb.auth.CreateAuthParam;
import net.floodlightcontroller.util.DirLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheBuilderSpec;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

/**
 * Session Manager backed by on disk json files. Takes ownership and locks the given directory
 * on start to avoid concurrent access.
 * <p>
 * Internally uses a ConcurrentHashMap and piggybacks on Google Guava's CacheBuilder
 * to create a backing map with configurable maximum size and expiry.
 * <p>
 * You can create a SimpleSessionManager with custom expiry settings by passing
 * in a spec string according to Guava's CacheBuilderSpec.
 * <p>
 * Default settings: maximumSize=1000000,expireAfterAccess=2h
 *
 * @see CacheBuilderSpec
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 **/
public class FileSessionManager implements SessionManager {
    private final static Logger logger = LoggerFactory.getLogger(FileSessionManager.class);

    private final AtomicLong sessionIdGenerator;

    public final String DEFAULT_EXPIRE_SPEC = "maximumSize=1000000,expireAfterAccess=2h";

    private final DirLock dirLock;

    // this is the *master* mapping of this manager
    private final ConcurrentMap<Long, Session> idSessionMap;
    // persistent store
    private final JsonFileDataStore<Session> fileSessionStore;

    // A loading cache
    private final SimpleSessionCookieManager cookieManager =
            new SimpleSessionCookieManager();

    private final SessionChangeListener sessionChangeListener = new SessionChangeListener();

    /** Create a FileSessionManager. Locks the sessionDir to avoid clashes. Then loads persisted JSON sessoins into
     *  memory.
     *
     * @param sessionDir the directory where the sessions are persisted. Should be empty except for session data.
     * @param cacheSpec size/expiry settings for the cache. See Guava's CacheBuilderSpec
     * @throws IOException if the dir is locked, or other exceptions ocur
     * @see CacheBuilderSpec
     */
    @CreateAuthComponent
    public FileSessionManager(@CreateAuthParam("sessionDir") File sessionDir,
            @CreateAuthParam("sessionCacheSpec") String cacheSpec) throws IOException {
        this(sessionDir, cacheSpec, null);
    }

    /** allows to specify a Ticker timesource to force expiry of elements. For testing */
    FileSessionManager(File sessionDir, String cacheSpec, Ticker timeSource) throws IOException {
        // piggyback on Google Guava's cache builder for awesome automatic
        // expiry of entries, loader delegate

        // only one guy should use the session dir at the same time
        dirLock = DirLock.lockDir(sessionDir);

        if (cacheSpec == null) {
            cacheSpec = SimpleSessionManager.DEFAULT_EXPIRE_SPEC;
        }

        CacheBuilder<Long, Session> builder = CacheBuilder.from(cacheSpec).recordStats().removalListener(new SessionCacheRemovalListener());
        if(timeSource != null)
            builder.ticker(timeSource);

        idSessionMap = builder.build().asMap();

        fileSessionStore =
                new JsonFileDataStore<Session>(sessionDir, Session.class, Pattern.compile("\\d{1,9}"));

        long maxSeenId = loadOldSessions();
        sessionIdGenerator = new AtomicLong(maxSeenId);
    }

    private long loadOldSessions() throws IOException {
        long maxSeenId = 0;

        for (String idString : fileSessionStore.listAllKeys()) {
            Session session = fileSessionStore.load(idString);

            long id = session.getId();

            if (id > maxSeenId)
                maxSeenId = id;

            Session previousSession = idSessionMap.putIfAbsent(session.getId(), session);
            if (previousSession != null) {
                logger.warn("Integrity violation when loading sessions session id " + id
                        + " clash: s1: " + previousSession + " new: " + session
                        + ". Ignoring new session.");
                continue;
            }
            cookieManager.putSessionCokie(session.getCookie(), session.getId());
            session.setSessionListener(sessionChangeListener);
        }
        return maxSeenId;
    }

    /** listens on the Guava cache, and removes expired elements from the
     *  disk and the cookie Manager. If that fails, logs an warning.
     */
    class SessionCacheRemovalListener implements RemovalListener<Long, Session> {
        @Override
        public void onRemoval(RemovalNotification<Long, Session> notification) {
            try {
                cookieManager.removeSessionForCookie(notification.getValue().getCookie());
                fileSessionStore.remove(notification.getKey().toString());
            } catch (IOException e) {
                logger.warn(e.getMessage(), e);
            }
        }
    }

    /** listens for change notifications on the sessions and updates the
     *  persisted session data on disk. If that fails, logs an warning.
     *
     */
    class SessionChangeListener implements SessionListener {
        @Override
        public void onSessionTouched(Session session) {
            // todo this should probably be done asynchronously
            try {
                fileSessionStore.save(Long.toString(session.getId()), session);
            } catch (IOException e) {
                logger.warn(
                        "Error saving session " + session + " to disk: " + e.getMessage(),
                        e);
            }
        }
    }

    @Override
    public Session createSession(BigDBUser user) {
        long sessionId = sessionIdGenerator.incrementAndGet();
        String cookie = cookieManager.createSessionCookie(sessionId);

        Session session = new Session(sessionId, cookie, user);
        if (idSessionMap.putIfAbsent(sessionId, session) != null)
            throw new IllegalStateException(
                    "Cannot create session - session id space exhausted. "
                            + "Do you seriously need more than 2^64 sessions?");

        try {
            fileSessionStore.save(Long.toString(session.getId()), session);
        } catch (IOException e) {
            throw new RuntimeException("Error saving session " + session + " to disk: "
                    + e.getMessage(), e);
        }
        session.setSessionListener(sessionChangeListener);

        return session;
    }

    @Override
    public Session getSessionForId(Long sessionId) {
        Session session = idSessionMap.get(sessionId);
        if (session != null)
            session.touch();
        return session;
    }

    @Override
    public Session getSessionForCookie(String cookie) {
        Long sessionId = cookieManager.getSessionIdForCookie(cookie);

        if (sessionId != null)
            return getSessionForId(sessionId);
        else
            return null;
    }

    @Override
    public boolean removeSession(Session session) {
        return idSessionMap.remove(session.getId()) != null;
    }

    @Override
    public Iterable<Session> getActiveSessions() {
        return idSessionMap.values();
    }

    @Override
    public void purgeAllSessions() {
        idSessionMap.clear();
    }

    /** unlocks the directory */
    public void stop() {
        try {
            dirLock.unlock();
        } catch (IOException e) {
            logger.warn("Error unlocking session dir: "+e.getMessage(),e);
        }
    }
}
