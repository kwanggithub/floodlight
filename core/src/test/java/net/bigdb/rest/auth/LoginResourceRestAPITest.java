package net.bigdb.rest.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;

import net.bigdb.auth.AuthConfig;
import net.bigdb.auth.session.SimpleSessionManager;
import net.bigdb.rest.BigDBRestAPITestBase;

import org.junit.BeforeClass;
import org.junit.Test;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** exercises the LoginResource via the
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class LoginResourceRestAPITest extends BigDBRestAPITestBase {
    protected final static Logger logger =
            LoggerFactory.getLogger(LoginResourceRestAPITest.class);

    @BeforeClass
    public static void testSetup() throws Exception {
        dbService = defaultService();
        authConfig = new AuthConfig("enabled=true,resetAdminPassword=adminpw")
            .setParam(AuthConfig.SESSION_MANAGER, SimpleSessionManager.class);
        setupBaseClass();
    }

    @Test
    public void testIncompleteRequest() throws Exception {
        try {
            ClientResource client = new ClientResource(Method.POST, REST_SERVER + "/api/v1/auth/login");
            client.post("{ \"user\": \"not-found\" }", Map.class);
            fail("Expected restlet ResourceException");
        } catch(ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_BAD_REQUEST, e.getStatus());
        }
    }

    @Test
    public void testUserNotFound() throws Exception {
        try {
            ClientResource client = new ClientResource(Method.POST, REST_SERVER + "/api/v1/auth/login");
            client.post("{ \"user\": \"not-found\", \"password\": \"hrtl\" }", Map.class);
            fail("Expected restlet ResourceException");
        } catch(ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_UNAUTHORIZED, e.getStatus());
        }
    }

    @Test
    public void testWrongPassword() throws Exception {
        try {
            ClientResource client = new ClientResource(Method.POST, REST_SERVER + "/api/v1/auth/login");
            client.post("{ \"user\": \"admin\", \"password\": \"notmypassword\" }", Map.class);
            fail("Expected restlet ResourceException");
        } catch(ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_UNAUTHORIZED, e.getStatus());
        }
    }

    @Test
    public void testSuccess() throws Exception {
        ClientResource client = new ClientResource(Method.POST, REST_SERVER + "/api/v1/auth/login");
        Map<?,?> map = client.post("{ \"user\": \"admin\", \"password\": \"adminpw\" }", Map.class);
        assertTrue(map != null);
        assertTrue(map.containsKey("success"));
        assertTrue((Boolean) map.get("success"));
        assertTrue(map.containsKey("session_cookie"));
        assertTrue( ((String) map.get("session_cookie")).length() > 12);
    }

    @Test
    public void testWithoutLoginUnauthorized() throws Exception {
        try {
            ClientResource client =
                    new ClientResource(Method.GET, REST_URL+"/core");
            client.get();
            fail("Expected restlet ResourceException");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_UNAUTHORIZED, e.getStatus());
        }
    }

}
