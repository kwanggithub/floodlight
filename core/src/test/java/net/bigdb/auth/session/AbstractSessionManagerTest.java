package net.bigdb.auth.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bigdb.auth.BigDBGroup;
import net.bigdb.auth.BigDBUser;

import org.junit.Before;
import org.junit.Test;
import com.google.common.base.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Ticker;
import com.google.common.collect.Iterators;

public abstract class AbstractSessionManagerTest<T extends SessionManager> {
    protected static Logger logger = LoggerFactory.getLogger(AbstractSessionManagerTest.class);

    protected BigDBUser mockUser;

    @Before
    public void setup() {
        mockUser = new BigDBUser("fred", "Fred FlintStone", Collections.<BigDBGroup>emptyList());
    }

    public abstract T getSessionManager(String cacheSpec, Ticker timeSource) throws Exception;

    @Test
    public void testBasic2Sessions() throws Exception {
        T manager = getSessionManager(null, null);

        long now = System.currentTimeMillis();

        Session session = manager.createSession(mockUser);
        session.setLastAddress("8.8.8.8");
        assertEquals(mockUser, session.getUser());
        assertTrue(session.getLastTouched() >= now);
        assertTrue(session.getTouchCount() == 1);

        assertTrue(session.getId() > 0);
        assertTrue(session.getCookie().length() > 20);

        now = System.currentTimeMillis();
        Session retrieveSession = manager.getSessionForId(session.getId());
        assertEquals(session.getUser(), retrieveSession.getUser());
        assertEquals(session.getCookie(), retrieveSession.getCookie());
        assertEquals(session.getId(), retrieveSession.getId());

        assertTrue("session touch should be updated", retrieveSession.getLastTouched() >= now);
        assertTrue(session.getTouchCount() == 2);

        now = System.currentTimeMillis();
        Session retrieveSession2 = manager.getSessionForId(session.getId());
        assertEquals(session.getUser(), retrieveSession2.getUser());
        assertEquals(session.getCookie(), retrieveSession2.getCookie());
        assertEquals(session.getId(), retrieveSession2.getId());

        assertTrue("session touch should be updated", retrieveSession2.getLastTouched() >= now);
        assertTrue(retrieveSession2.getTouchCount() == 3);


        now = System.currentTimeMillis();
        Session otherSession = manager.createSession(mockUser);
        otherSession.setLastAddress("123.123.123.123");
        assertFalse("new session should get different cookie", Objects.equal(session.getCookie(), otherSession.getCookie()));
        assertTrue("new session should get a higher id", otherSession.getId() > session.getId());
        assertTrue("session touch should be updated", otherSession.getLastTouched() >= now);
        assertTrue("session touch should be updated", otherSession.getTouchCount() == 1);

        assertTrue("original session touch should NOT be updated", session.getLastTouched() <= now);
        assertTrue(retrieveSession2.getTouchCount() == 3);

        postTestBasic2Session(manager, session, otherSession);
    }


    protected void postTestBasic2Session(T manager, Session s1, Session s2) throws Exception {
    }

    @Test
    public void testMaxSize5() throws Exception {
        T manager = getSessionManager("maximumSize=5", null);
        List<Session> sessions = new ArrayList<Session>();
        for(int i=0; i < 6; i++) {
            Session session = manager.createSession(mockUser);
            sessions.add(session);
        }
        assertEquals(5, Iterators.size(manager.getActiveSessions().iterator()));
        assertNull(manager.getSessionForId(sessions.get(0).getId()));
        assertNull(manager.getSessionForCookie(sessions.get(0).getCookie()));
        assertNotNull(manager.getSessionForId(sessions.get(1).getId()));
        assertNotNull(manager.getSessionForCookie(sessions.get(1).getCookie()));

        postTestMaxSize5(manager, sessions);
    }


    protected void postTestMaxSize5(T manager, List<Session> sessions) {
    }

    @Test
    public void test2SessionsRemove2nd() throws Exception {
        T manager = getSessionManager(null, null);

        Session session = manager.createSession(mockUser);
        Session session2 = manager.createSession(mockUser);
        manager.removeSession(session2);
        Session retrievedByCookie = manager.getSessionForCookie(session.getCookie());
        checkRetrievedSession(session, retrievedByCookie);
        postTest2SessionsRemove2nd(manager, session, session2);
    }

    protected void postTest2SessionsRemove2nd(T manager,Session s1, Session s2) {
    }

    @Test
    public void testExpireAfterAccess2h() throws Exception {
        FakeTimeSource timeSource = new FakeTimeSource();
        SessionManager manager = getSessionManager("expireAfterAccess=2h", timeSource);

        Session session = manager.createSession(mockUser);
        timeSource.setTime(1, 58, 0);

        Session retrievedByCookie = manager.getSessionForCookie(session.getCookie());
        checkRetrievedSession(session, retrievedByCookie);

        Session retrievedBydId = manager.getSessionForId(session.getId());
        checkRetrievedSession(session, retrievedBydId);

        timeSource.setTime(3, 59, 0);
        assertNull(manager.getSessionForCookie(session.getCookie()));
        assertNull(manager.getSessionForId(session.getId()));
    }


    @Test
    public void testExpireHard() throws Exception {
        FakeTimeSource timeSource = new FakeTimeSource();
        SessionManager manager = getSessionManager("expireAfterWrite=2h", timeSource);

        Session session = manager.createSession(mockUser);
        timeSource.setTime(1, 59, 0);

        Session retrievedSessionByCookie = manager.getSessionForCookie(session.getCookie());
        checkRetrievedSession(session, retrievedSessionByCookie);

        Session retrievedSessionById = manager.getSessionForId(session.getId());
        checkRetrievedSession(session, retrievedSessionById);

        timeSource.setTime(2, 1, 0);
        assertNull(manager.getSessionForCookie(session.getCookie()));
        assertNull(manager.getSessionForId(session.getId()));
    }

    protected void checkRetrievedSession(Session session, Session retrievedSession) {
        assertNotNull(retrievedSession);
        assertEquals(session.getUser(), retrievedSession.getUser());
        assertEquals(session.getCookie(), retrievedSession.getCookie());
        assertEquals(session.getId(), retrievedSession.getId());
        assertEquals(session.getLastAddress(), retrievedSession.getLastAddress());
    }

    class FakeTimeSource extends Ticker {
        long time;

        @Override
        public long read() {
            return time;
        }

        public void setTime(int h, int m, int s) {
            time = (h * 3600 + m * 60 + s) * 1000000000L;
        }
    }
}
