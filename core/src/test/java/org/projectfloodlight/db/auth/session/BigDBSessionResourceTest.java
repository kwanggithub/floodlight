package org.projectfloodlight.db.auth.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.auth.BigDBGroup;
import org.projectfloodlight.db.auth.BigDBUser;
import org.projectfloodlight.db.auth.session.BigDBSessionResource;
import org.projectfloodlight.db.auth.session.Session;
import org.projectfloodlight.db.auth.session.SimpleSessionManager;
import org.projectfloodlight.db.auth.session.BigDBSessionResource.SessionInfo;
import org.projectfloodlight.db.query.Query;

import com.google.common.collect.Iterables;

public class BigDBSessionResourceTest {
    private SimpleSessionManager sessionManager;
    private Session s;
    private Session s2;

    @Before
    public void setup() {
        sessionManager = new SimpleSessionManager();
    }

    @Test
    public void testListSessionsEmpty() throws Exception {
        BigDBSessionResource resource = new BigDBSessionResource(sessionManager);
        Iterator<SessionInfo> i= resource.getSessions(Query.parse("/core/aaa/sessions"), null);
        assertFalse("Session List should be empty", i.hasNext());
    }

    @Test
    public void testListSessionsOne() throws Exception {
        Session s = sessionManager.createSession(new BigDBUser("foo", "bar", Collections.singletonList(new BigDBGroup("admin"))));
        assertNotNull(s);

        BigDBSessionResource resource = new BigDBSessionResource(sessionManager);
        Iterator<SessionInfo> i = resource.getSessions(Query.parse("/core/aaa/sessions"), null);
        assertTrue(i.hasNext());
        SessionInfo first = i.next();
        checkSessionInfo(s, first);

        assertFalse(i.hasNext());
    }

    private void checkSessionInfo(Session s, SessionInfo first) {
        assertEquals(first.getId(), s.getId());
        assertEquals(s.getCreated(), first.getCreated());
        assertEquals(s.getLastTouched(), first.getLastTouched());

        assertEquals(first.getUserInfo().getUserName(), s.getUser().getUser());
        assertEquals(first.getUserInfo().getFullName(), s.getUser().getFullName());
        assertEquals(Collections.singletonList("admin"), first.getUserInfo().getGroup());
    }

    @Test
    public void testListSessionsTwo() throws Exception {
        sessionManagerWithTwoSessions();

        BigDBSessionResource resource = new BigDBSessionResource(sessionManager);
        Iterator<SessionInfo> i = resource.getSessions(Query.parse("session"), null);
        assertTrue(i.hasNext());
        checkSessionInfo(s, i.next());
        assertTrue(i.hasNext());
        checkSessionInfo(s2, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testListSessionsTwoSelectOneById() throws Exception {
        sessionManagerWithTwoSessions();

        BigDBSessionResource resource = new BigDBSessionResource(sessionManager);
        Iterator<SessionInfo> i = resource.getSessions(Query.parse("session[id="+s2.getId()+"]"), null);
        assertTrue(i.hasNext());
        checkSessionInfo(s2, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testListSessionsTwoSelectOneByIdNonExisting() throws Exception {
        sessionManagerWithTwoSessions();

        BigDBSessionResource resource = new BigDBSessionResource(sessionManager);
        Iterator<SessionInfo> i = resource.getSessions(Query.parse("session[id=1252]"), null);
        assertFalse(i.hasNext());
    }

    @Test
    public void testListSessionsTwoSelectOneByCookie() throws Exception {
        sessionManagerWithTwoSessions();

        BigDBSessionResource resource = new BigDBSessionResource(sessionManager);
        AuthContext authContext = AuthContext.forSession(s2, null);
        Iterator<SessionInfo> i = resource.getSessions(Query.parse("session[auth-token='"+s2.getCookie()+"']"), authContext);
        assertTrue(i.hasNext());
        checkSessionInfo(s2, i.next());
        assertFalse(i.hasNext());
    }

    @Test
    public void testListSessionsTwoSelectOneByCookieNonExisting() throws Exception {
        sessionManagerWithTwoSessions();

        BigDBSessionResource resource = new BigDBSessionResource(sessionManager);
        Iterator<SessionInfo> i = resource.getSessions(Query.parse("session[auth-token='foobar']"), null);
        assertFalse(i.hasNext());
    }

    @Test
    public void testDeleteSessionByCookie() throws Exception {
        sessionManagerWithTwoSessions();
        assertEquals(2, Iterables.size(sessionManager.getActiveSessions()));

        BigDBSessionResource resource = new BigDBSessionResource(sessionManager);
        resource.deleteSession(Query.parse("session[auth-token='"+s.getCookie() +"']"));
        assertEquals(1, Iterables.size(sessionManager.getActiveSessions()));
        assertEquals(Iterables.get(sessionManager.getActiveSessions(), 0), s2);
    }

    @Test
    public void testDeleteSessionById() throws Exception {
        sessionManagerWithTwoSessions();
        assertEquals(2, Iterables.size(sessionManager.getActiveSessions()));

        BigDBSessionResource resource = new BigDBSessionResource(sessionManager);
        resource.deleteSession(Query.parse("session[id="+s2.getId() +"]"));
        assertEquals(1, Iterables.size(sessionManager.getActiveSessions()));
        assertEquals(Iterables.get(sessionManager.getActiveSessions(), 0), s);
    }

    private void sessionManagerWithTwoSessions() {
        s = sessionManager.createSession(new BigDBUser("foo", "bar", Collections.singletonList(new BigDBGroup("admin"))));
        s2 = sessionManager.createSession(new BigDBUser("yahoo", "bar", Collections.singletonList(new BigDBGroup("admin"))));
        assertNotNull(s);
        assertNotNull(s2);
    }

}
