package org.projectfloodlight.db.rest.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Map;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.db.auth.AuditEvent;
import org.projectfloodlight.db.auth.Auditor;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.auth.AuthService;
import org.projectfloodlight.db.auth.application.ApplicationRegistration;
import org.projectfloodlight.db.auth.session.SimpleSessionManager;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.rest.BigDBRestAPITestBase;
import org.projectfloodlight.db.service.Treespace;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Method;
import org.restlet.engine.header.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;

/** Test various paths into the accounting framework
 *
 * Cribbed from ShowSessionTest and LoginResourceRestAPITest
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public class RestAccountingTest extends BigDBRestAPITestBase {

    protected final static Logger logger =
            LoggerFactory.getLogger(RestAccountingTest.class);

    private String sessionCookie = null;
    private ApplicationRegistration app = null;
    // auth settings for *this* connection

    protected static AuthService authService;
    private static TestAuditor testAuditor = new TestAuditor();

    private int lastEventCount = 0;
    private int lastAuditEventCount = 0;

    private static File tmpDir1;
    private static File tmpDir2;
    private static ApplicationRegistration app1;
    private static ApplicationRegistration app2;

    private static class TestAuditor implements Auditor {

        private ArrayList<AuditEvent> events = new ArrayList<AuditEvent>();

        public static final String TEST_EVENT = "testEvent";

        @Override
        public void write(AuditEvent event) {
            logger.info("received event {}", event.getType());
            events.add(event);
        }

        public int getEventCount() {
            return events.size();
        }

        public int getAuditEventCount() {
            int count = 0;
            for (AuditEvent e : events) {
                if (e.getType().equals(TEST_EVENT))
                    ++count;
            }
            return count;
        }
        
        public void reset() {
            events.clear();
        }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        tmpDir1 = basePath.newFolder();
        tmpDir2 = basePath.newFolder();
        File secret2 = new File(tmpDir2, "app2.json");

        dbService = defaultService();
        authConfig = new AuthConfig("enabled=true,resetAdminPassword=adminpw")
            .setParam(AuthConfig.SESSION_MANAGER, SimpleSessionManager.class)
            .setParam(AuthConfig.APPLICATION_DIR, tmpDir1)
            .setParam(AuthConfig.APPLICATIONS, String.format("app1,app2=%s", secret2.getPath()));
        setupBaseClass();

        initAccounting();
        initApplications();

        logger.info("testSetup done");
    }

    protected static void initAccounting() throws Exception {

        authService = dbService.getService().getAuthService();
        authService.registerAuditor("testaudit", testAuditor);

        // Add users
        Query query;
        String replaceData;
        InputStream input;

        query = Query.parse("/core/aaa/accounting");
        replaceData = "[{" +
                "\"name\":\"syslog\"" +
                "}, {" +
                "\"name\":\"testaudit\"" +
                "}]";
        input = new ByteArrayInputStream(replaceData.getBytes("UTF-8"));
        Treespace tp = dbService.getControllerTreespace();
        tp.replaceData(query, Treespace.DataFormat.JSON, input,
                       AuthContext.SYSTEM);
    }

    @Before
    public void resetAccounting() {
        lastEventCount = 0;
        lastAuditEventCount = 0;
        testAuditor.reset();
    }
    
    protected static void initApplications() throws Exception {

        for (ApplicationRegistration reg : authService.getApplicationAuthenticator()) {
            if (reg.getName().equals("app1"))
                app1 = reg;
            if (reg.getName().equals("app2"))
                app2 = reg;
        }

        File secretFile = new File(tmpDir1, "app1.json");
        ObjectMapper om = new ObjectMapper();
        Reader reader = Files.newReader(secretFile, Charset.forName("UTF-8"));
        ApplicationRegistration app = om.readValue(reader, ApplicationRegistration.class);
        assertEquals(app.getName(), "app1");
        assertEquals(app1.getSecret(), app.getSecret());

        secretFile = new File(tmpDir2, "app2.json");
        reader = Files.newReader(secretFile, Charset.forName("UTF-8"));
        app = om.readValue(reader, ApplicationRegistration.class);
        assertEquals(app.getName(), "app2");
        assertEquals(app2.getSecret(), app.getSecret());

    }

    private void doLogin() throws Exception {
        ClientResource client = new ClientResource(Method.POST, REST_SERVER + "/api/v1/auth/login");
        Map<?,?> map = client.post("{ \"user\": \"admin\", \"password\": \"adminpw\" }", Map.class);
        assertTrue(map != null);
        assertTrue(map.containsKey("success"));
        assertTrue((Boolean) map.get("success"));
        assertTrue(map.containsKey("session_cookie"));
        assertTrue(((String) map.get("session_cookie")).length() > 12);
        sessionCookie = (String) map.get("session_cookie");
    }

    // sample REST query that requires authorization

    private void updateHeaders(Request request) {
        @SuppressWarnings("unchecked")
        Series<Header> headers = (Series<Header>) request.getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
        if (headers == null) {
            headers = new Series<Header>(Header.class);
            request.getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, headers);
        }
        if (sessionCookie != null)
            headers.add("Cookie", "session_cookie=" + sessionCookie);
        if (app != null)
            headers.add("Authorization", app.getBasicAuth());
    }

    /** doGetSchema should succeed without proper authorization
     *
     */
    private void doGetSchema() {
        Request req = new Request(Method.GET, REST_SERVER + "/api/v1/schema/controller");
        Response rsp = new Response(req);
        ClientResource client = new ClientResource(req, rsp);
        updateHeaders(req);
        client.get();
    }

    /** doGetSessions requires a user login
     *
     */
    private void doGetSessions() {
        Request req = new Request(Method.GET, REST_URL + "/core/aaa/session");
        Response rsp = new Response(req);
        ClientResource client = new ClientResource(req, rsp);
        updateHeaders(req);
        client.get();
    }

    /** doAccounting requires an application key
    *
    */
    private void doAccounting() {
        Request req = new Request(Method.GET, REST_URL + "/core/aaa/audit-event");
        Response rsp = new Response(req);
        ClientResource client = new ClientResource(req, rsp);
        updateHeaders(req);
        client.post("{ \"event-type\" : \"" + TestAuditor.TEST_EVENT+ "\" }");
    }

    /** make sure we accounted for just what we are authorized for
     *
     * @throws Exception
     */
    private void assertAccounting(int global, int local) {
        int delta = testAuditor.getEventCount() - lastEventCount;
        assertEquals(global, delta);
        lastEventCount += delta;
        delta = testAuditor.getAuditEventCount() - lastAuditEventCount;
        assertEquals(local, delta);
        lastAuditEventCount += delta;
    }

    @Test
    public void testUserSessionAccounting() throws Exception {
        assertAccounting(0, 0);
        doLogin();
        // create session + login
        assertAccounting(2, 0);
        doGetSchema();
        assertAccounting(1, 0);
        doGetSessions();
        assertAccounting(1, 0);
        doAccounting();
        assertAccounting(1, 1);
    }

    @Test
    public void testMissingCookieSessionAccounting() throws Exception {
        sessionCookie = null;
        assertAccounting(0, 0);

        // rest accounting succeeds (recording 401 failure), but request fails
        try {
            doGetSessions();
            fail("should have thrown an exception");
        } catch (ResourceException e) {}
        assertAccounting(1, 0);

        // accounting of audit-event is suppressed,
        // actual post to audit-event fails
        try {
            doAccounting();
            fail("should have thrown an exception");
        } catch (ResourceException e) {}
        assertAccounting(0, 0);
    }

    /* same results as for the missing cookie */
    @Test
    public void testInvalidCookieSessionAccounting() throws Exception {

        doLogin();
        sessionCookie = "NOT_" + sessionCookie;
        doGetSchema();
        assertAccounting(3, 0);

        // rest accounting succeeds, but request fails
        try {
            doGetSessions();
            fail("should have thrown an exception");
        } catch (ResourceException e) {}
        assertAccounting(1, 0);

        // audit-event accounting is suppressed
        // actual post to audit-event fails
        try {
            doAccounting();
            fail("should have thrown an exception");
        } catch (ResourceException e) {}
        assertAccounting(0, 0);
    }

    /* test with just an application key */
    @Test
    public void testApplicationAccounting() throws Exception {

        sessionCookie = null;
        app = app1;

        assertAccounting(0, 0);

        try {
            doGetSessions();
            logger.warn("should have thrown an exception");
        } catch (ResourceException e) {}
        assertAccounting(1, 0);

        // application key is sufficient to generate an accounting record
        doAccounting();
        assertAccounting(1, 1);
    }

    @Test
    public void testUnknownApplicationAccounting() throws Exception {

        sessionCookie = null;
        app = new ApplicationRegistration(app1.getName() + "2", app1.getSecret());

        assertAccounting(0, 0);

        try {
            doGetSessions();
            fail("XXX roth -- BSC-4010 -- should have thrown an exception");
        } catch (ResourceException e) {}
        assertAccounting(1, 0);

        // application key is sufficient to generate an accounting record
        try {
            doAccounting();
            fail("should have thrown an exception");
        } catch (ResourceException e) {}
        assertAccounting(0, 0);
    }

    /* with a valid cookie *and* a valid app token */
    @Test
    public void testAuthenticatedCli() throws Exception {

        doLogin();
        assertAccounting(2, 0);

        app = app1;

        doGetSessions();
        assertAccounting(1, 0);

        doAccounting();
        assertAccounting(1, 1);
    }

    /* with an invalid cookie *and* a valid app token */
    @Test
    public void testExpiredCli() throws Exception {

        doLogin();
        sessionCookie = "NOT_" + sessionCookie;
        assertAccounting(2, 0);

        app = app1;

        try {
            doGetSessions();
            fail("XXX roth -- BSC-4010 -- should have thrown an exception");
        } catch (ResourceException e) {}
        assertAccounting(1, 0);

        doAccounting();
        assertAccounting(1, 1);
    }

}
