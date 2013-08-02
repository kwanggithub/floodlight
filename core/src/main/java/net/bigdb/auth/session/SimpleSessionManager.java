package net.bigdb.auth.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.bigdb.auth.BigDBUser;
import net.bigdb.auth.CreateAuthComponent;
import net.bigdb.auth.CreateAuthParam;

import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheBuilderSpec;


/** Simple in-memory Session Manager. Piggy backs on Google Guava's CacheBuilder to create
 *  a backing map with configurable maximum size and expiry.
 *  <p>
 *  You can create a SimpleSessionManager with custom expiry settings by passing in
 *  a spec string according to Guava's CacheBuilderSpec.
 *  <p>
 *  Default settings: maximumSize=1000000,expireAfterAccess=2h
 *  @see CacheBuilderSpec
 *
 *  @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 **/
public class SimpleSessionManager implements SessionManager {
    private final AtomicInteger sessionIdGenerator;

    public final static String DEFAULT_EXPIRE_SPEC = "maximumSize=1000000,expireAfterAccess=2h";

    // this is the *master* mapping of this manager
    private final ConcurrentMap<Long, Session> idToSessionMap;

    private final SessionCookieManager cookieManager;

    /** create a SimpleSessionManager with default settings
     *  (1 Mio max entries, expiry after access: 2h)
     */
    public SimpleSessionManager() {
        this(null, null);
    }

    /** create a SimpleSessionManager using the given cache spec (see CacheBuilderSpec)
     * @see CacheBuilderSpec
     */
    @CreateAuthComponent
    public SimpleSessionManager(@CreateAuthParam("sessionCacheSpec") String cacheSpec) {
        this(cacheSpec, null);
    }

    /** for unit tests, create a SimpleSessionManager with custom expiry time + timeunit */
    SimpleSessionManager(String cacheSpec, Ticker timeSource) {
        if(cacheSpec == null) {
            cacheSpec = DEFAULT_EXPIRE_SPEC;
        }

        sessionIdGenerator = new AtomicInteger(0);
        // piggyback on Google Guava's cache builder for awesome automatic expiry
        // of entries
        CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.from(cacheSpec).recordStats();

        if(timeSource != null)
            cacheBuilder.ticker(timeSource);

        idToSessionMap = cacheBuilder.<Long, Session>build().asMap();

        cookieManager = new SimpleSessionCookieManager();
    }


    @Override
    public Session createSession(BigDBUser user) {
        long sessionId = sessionIdGenerator.incrementAndGet();
        String cookie = cookieManager.createSessionCookie(sessionId);

        Session session = new Session(sessionId, cookie, user);
        if(idToSessionMap.putIfAbsent(sessionId, session) != null)
            throw new IllegalStateException("Cannot create session - session id space exhausted. "+
                   "Do you seriously need more than 2^64 sessions?");

        return session;
    }

    @Override
    public Session getSessionForId(Long sessionId) {
        Session session = idToSessionMap.get(sessionId);
        if(session != null)
            session.touch();
        return session;
    }

    @Override
    public Session getSessionForCookie(String cookie) {
        Long sessionId = cookieManager.getSessionIdForCookie(cookie);

        if(sessionId != null)
            return getSessionForId(sessionId);
        else
            return null;
    }

    @Override
    public boolean removeSession(Session session) {
        cookieManager.removeSessionForCookie(session.getCookie());
        return idToSessionMap.remove(session.getId()) != null;
    }

    @Override
    public Iterable<Session> getActiveSessions() {
        ArrayList<Session> res = new ArrayList<Session>(idToSessionMap.values());
        Collections.sort(res);
        return res;
    }

    @Override
    public void purgeAllSessions() {
        idToSessionMap.clear();
    }

}
