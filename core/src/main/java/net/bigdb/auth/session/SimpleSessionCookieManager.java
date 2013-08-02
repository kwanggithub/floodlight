package net.bigdb.auth.session;

import java.util.concurrent.ConcurrentMap;

import org.jboss.netty.util.internal.ConcurrentHashMap;


/** Simple in-memory Session Cookie Manager backed by a concurrent hashmap */
class SimpleSessionCookieManager implements SessionCookieManager {
    private static final int MAX_COOKIE_ATTEMPTS = 100;

    private final ConcurrentMap<String, Long> cookieToIdMap;
    private final RandomCookieGenerator cookieGenerator;

    public SimpleSessionCookieManager() {
        cookieToIdMap = new ConcurrentHashMap<String, Long>();
        cookieGenerator = new RandomCookieGenerator();
    }

    @Override
    public String createSessionCookie(Long sessionId) {
        for(int i=0; i < MAX_COOKIE_ATTEMPTS; i++) {
            String sessionCookie = cookieGenerator.createCookie();
            if(cookieToIdMap.putIfAbsent(sessionCookie, sessionId) == null)
                return sessionCookie;
        }
        // WAT? The user managed to randomly generate a duplicate cookie 100 times?!
        throw new IllegalStateException("100 duplicate cookies. Dude, you're really unlucky. Never play poker.");
    }

    @Override
    public Long getSessionIdForCookie(String cookie) {
        return cookieToIdMap.get(cookie);
    }

    @Override
    public boolean removeSessionForCookie(String cookie) {
        return cookieToIdMap.remove(cookie) != null;
    }

    public void putSessionCokie(String cookie, long id) {
        Long oldId = cookieToIdMap.putIfAbsent(cookie, id);
        if (oldId != null) {
            throw new IllegalArgumentException("Integrity violation: session with cookie "+cookie + " already there: id="+oldId);
        }
    }

}
