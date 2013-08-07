package org.projectfloodlight.db.auth.session;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.AuthService;
import org.projectfloodlight.db.auth.BigDBGroup;
import org.projectfloodlight.db.auth.BigDBUser;
import org.projectfloodlight.db.auth.NullAuthorizationHook;
import org.projectfloodlight.db.auth.session.Session;
import org.projectfloodlight.db.auth.session.SimpleSessionManager;
import org.projectfloodlight.db.rest.BigDBRestAPITestBase;
import org.projectfloodlight.db.rest.auth.LoginResource;
import org.restlet.data.Cookie;
import org.restlet.data.Method;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** exercises the Session REST API in BigBD
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class BigDBSessionResourceRestAPITest extends BigDBRestAPITestBase {
    protected static Logger logger =
            LoggerFactory.getLogger(BigDBSessionResourceRestAPITest.class);

    private static AuthService authService;

    @BeforeClass
    public static void testSetup() throws Exception {
        dbService = defaultService();
        authConfig = new AuthConfig("enabled=true, enableNullAuthentication=true")
                                   .setParam(AuthConfig.DEFAULT_QUERY_PREAUTHORIZATION_HOOK, NullAuthorizationHook.class)
                                   .setParam(AuthConfig.DEFAULT_QUERY_AUTHORIZATION_HOOK, NullAuthorizationHook.class)
                                   .setParam(AuthConfig.SESSION_MANAGER, SimpleSessionManager.class);
        setupBaseClass();
        authService = dbService.getService().getAuthService();
    }

    private BigDBUser user;

    @Before
    public void setup() {
        user = new BigDBUser("admin", "default admin", Collections.singleton(new BigDBGroup("admin")));
    }

    @After
    public void tearDown() {
        authService.getSessionManager().purgeAllSessions();
    }

    @Test
    public void testListSessionsOperations() throws Exception {
        listAndCheckSessions(); // empty

        Session s1 = authService.getSessionManager().createSession(user);
        listAndCheckSessions(s1); // only s1
        Session s2 = authService.getSessionManager().createSession(user);
        listAndCheckSessions(s1, s2); // only s1, s2
        Session s3 = authService.getSessionManager().createSession(user);
        listAndCheckSessions(s1, s2, s3); // only s1, s2
        authService.getSessionManager().removeSession(s2);
        listAndCheckSessions(s1, s3); // only s1, s2
    }

    @Test
    public void testDeleteSessionsById() throws Exception {
        listAndCheckSessions(); // empty
        Session s1 = authService.getSessionManager().createSession(user);
        deleteSessionById(s1.getId(), s1);
    }

    @Test
    public void testDeleteSessionsByCookie() throws Exception {
        listAndCheckSessions(); // empty
        Session s1 = authService.getSessionManager().createSession(user);
        deleteSessionByCookie(s1.getCookie(), s1);
        listAndCheckSessions(); // empty
    }

    public void deleteSessionById(long id, Session session) {
        ClientResource client = new ClientResource(Method.DELETE, REST_URL + "/core/aaa/session[id="+id+"]");
        Series<Cookie> cookies = new Series<Cookie>(Cookie.class);
        cookies.add(new Cookie(LoginResource.COOKIE_NAME, session.getCookie()));
        client.setCookies(cookies);
        client.delete();
    }

    public void deleteSessionByCookie(String cookie, Session session) {
        ClientResource client = new ClientResource(Method.DELETE, REST_URL + "/core/aaa/session[auth-token=\""+cookie+"\"]");
        Series<Cookie> cookies = new Series<Cookie>(Cookie.class);
        cookies.add(new Cookie(LoginResource.COOKIE_NAME, session.getCookie()));
        client.setCookies(cookies);
        client.delete();
    }

    @Test
    public void testGetSessionsById() throws Exception {
        listAndCheckSessions(); // empty
        Session s1 = authService.getSessionManager().createSession(user);
        checkGetSessionById(s1.getId(), s1);
        checkGetSessionById(1234, null);
        Session s2 = authService.getSessionManager().createSession(user);
        checkGetSessionById(s1.getId(), s1);
        checkGetSessionById(s2.getId(), s2);
        checkGetSessionById(1234, null);
        authService.getSessionManager().removeSession(s1);
        checkGetSessionById(s1.getId(), null);
    }

    @Test
    public void testGetSingleSessionById() throws Exception {
        Session session = authService.getSessionManager().createSession(user);
        ClientResource client = new ClientResource(Method.GET, REST_URL +
                "/core/aaa/session[id="+session.getId()+"]?single=true");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = client.get(Map.class);
        checkSession(session, result);
    }

    @Test
    public void testSessionNotFoundError() throws Exception {
        ClientResource client = new ClientResource(Method.GET, REST_URL +
                "/core/aaa/session[id=13567]?single=true");
        try {
            @SuppressWarnings({ "unchecked", "unused" })
            Map<String, Object> result = client.get(Map.class);
            fail("Expected a 404 error");
        }
        catch (ResourceException e) {
            assertEquals(404, e.getStatus().getCode());
        }
    }

    @SuppressWarnings("unchecked")
    public void checkGetSessionById(long id, Session session) {
        ClientResource client = new ClientResource(Method.GET, REST_URL + "/core/aaa/session[id="+id+"]");
        List<Map<String, Object>> result = client.get(List.class);
        if (session != null) {
            assertEquals(1, result.size());
            Map<String, Object> sessionMap = result.get(0);
            checkSession(session, sessionMap);
        } else {
            assertEquals(0, result.size());
        }
    }

    public void listAndCheckSessions(Session... sessions) {
        ClientResource client = new ClientResource(Method.GET, REST_URL + "/core/aaa/session");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = client.get(List.class);
        assertTrue(result != null);
        assertEquals(sessions.length, result.size());
        for(int i=0; i < sessions.length; i++)
            checkSession(sessions[i],result.get(i));
    }

    private void checkSession(Session s1, Map<String, Object> s1Hash) {
        assertEquals((int) s1.getId(), s1Hash.get("id"));
        assertNull(s1Hash.get("auth-token"));
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfoHash = (Map<String, Object>) s1Hash.get("user-info");
        assertEquals(userInfoHash.get("user-name"), user.getUser());
    }


}
