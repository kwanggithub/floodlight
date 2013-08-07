package org.projectfloodlight.db.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.IBigDBService;
import org.projectfloodlight.db.MockBigDBService;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.auth.AuthenticationException;
import org.projectfloodlight.db.auth.AuthenticationResult;
import org.projectfloodlight.db.auth.BigDBAuthenticatorRegistry;
import org.projectfloodlight.db.auth.BigDBGroup;
import org.projectfloodlight.db.auth.DummyAuthenticator;
import org.projectfloodlight.db.auth.DynamicAuthenticator;
import org.projectfloodlight.db.auth.LocalAuthenticator;
import org.projectfloodlight.db.auth.DummyAuthenticator.Method;
import org.projectfloodlight.db.auth.password.PasswordHasherMd5;
import org.projectfloodlight.db.auth.session.SimpleSessionManager;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.rest.auth.LoginRequest;
import org.projectfloodlight.db.service.Treespace;
import org.restlet.data.ClientInfo;
import org.restlet.data.Status;

public class DynamicMethodListTest {

    protected LocalAuthenticator localAuthenticator;
    protected Method dummyAuthenticator;
    protected DynamicAuthenticator authenticator;
    protected MockBigDBService bigDB;
    protected Treespace treespace;
    protected BigDBAuthenticatorRegistry registry;

    // Acts just like the DummyAuthenticator, but adds the 'other' group membership.
    public class OtherMethod extends DummyAuthenticator.Method {
        @Override
        public AuthzResult getGroups(AuthnResult principalContext,
                                     LoginRequest request, ClientInfo clientInfo)
                                             throws AuthenticationException {
            AuthzResult r = super.getGroups(principalContext, request, clientInfo);
            if (!r.isSuccess())
                return r;
            HashSet<String> groups = new HashSet<String>(r.getGroups());
            groups.add("other");
            return AuthzResult.success(groups);
        }
    }

    protected OtherMethod otherAuthenticator;

    @Before
    public void setUp() throws Exception {
        FloodlightModuleContext fmc = new FloodlightModuleContext();

        AuthConfig ac = new AuthConfig();
        ac.setParam(AuthConfig.AUTH_ENABLED, true);
        ac.setParam(AuthConfig.SESSION_MANAGER, SimpleSessionManager.class);
        ac.setParam(AuthConfig.RESET_ADMIN_PASSWORD, "abc");

        bigDB = new MockBigDBService();
        bigDB.addModuleSchema("floodlight", "2012-10-22");
        bigDB.addModuleSchema("aaa");
        bigDB.setAuthConfig(ac);
        bigDB.init(fmc);
        bigDB.startUp(fmc);
        fmc.addService(IBigDBService.class, bigDB);

        localAuthenticator = new LocalAuthenticator(bigDB.getService(), new PasswordHasherMd5());
        dummyAuthenticator = new Method();
        otherAuthenticator = new OtherMethod();

        registry = new BigDBAuthenticatorRegistry(bigDB.getService());
        /* all authenticators should have auto-registered by now */
        authenticator = new DynamicAuthenticator(bigDB.getService(), localAuthenticator, registry);

        registry.registerAuthenticator(DummyAuthenticator.Method.NAME, dummyAuthenticator);
        registry.registerAuthenticator(LocalAuthenticator.NAME, localAuthenticator);
        registry.registerAuthenticator("other", otherAuthenticator);

        treespace = bigDB.getControllerTreespace();

        initUsersAndGroups();
        initMethodLists();
    }

    /*
     * XXX roth -- shamelessly copied here
     * @see org.projectfloodlight.db.auth.LocalAuthenticatorTest#initUsersAndGroups()
     */
    protected void initUsersAndGroups()
            throws BigDBException, UnsupportedEncodingException {
        // Add users
        Query query;
        String replaceData;
        InputStream input;

        query = Query.parse("/core/aaa/local-user");
        replaceData = "[{" +
                "\"user-name\":\"admin\"," +
                "\"password\": \"21232f297a57a5a743894a0e4a801fc3\"," +
                "\"full-name\":\"Ed Ming\"" +
                "}, {" +
                "\"user-name\":\"goofy\"," +
                "\"password\": \"\"," +
                "\"full-name\":\"Go Ofy\"" +
                "}, {" +
                "\"user-name\":\"no-full-name\"," +
                "\"password\": \"\"" +  // user doesn't have full name set
                "}]";
        input = new ByteArrayInputStream(replaceData.getBytes("UTF-8"));
        treespace.replaceData(query, Treespace.DataFormat.JSON, input,
                              AuthContext.SYSTEM);
    }

    /*
     * XXX roth -- shamelessly copied here
     * @see org.projectfloodlight.db.auth.LocalAuthenticatorTest#initUsersAndGroups()
     */
    protected void initMethodLists()
            throws BigDBException, UnsupportedEncodingException {
        // Add users
        Query query;
        String replaceData;
        InputStream input;

        query = Query.parse("/core/aaa/authenticator");
        replaceData = "["
                + "{"
                +   "\"priority\":10"
                +   "," + "\"name\":\"not-a-method\""
                + "}"
                + ","
                + "{"
                +   "\"priority\":20"
                +   "," + "\"name\":\"also-not-a-method\""
                + "}"
                + ","
                + "{"
                +   "\"priority\":100"
                +   "," + "\"name\":\"local\""
                + "}"
                + ","
                + "{"
                +   "\"priority\":90"
                +   "," + "\"name\":\"dummy\""
                + "}"
                + ","
                + "{"
                +   "\"priority\":200"
                +   "," + "\"name\":\"other\""
                + "}"
                + "]";
        input = new ByteArrayInputStream(replaceData.getBytes("UTF-8"));
        treespace.replaceData(query, Treespace.DataFormat.JSON, input,
                              AuthContext.SYSTEM);

        query = Query.parse("/core/aaa/authorizer");
        input = new ByteArrayInputStream(replaceData.getBytes("UTF-8"));
        treespace.replaceData(query, Treespace.DataFormat.JSON, input,
                              AuthContext.SYSTEM);
    }

    // make the 'other' authorizer come after 'dummy'
    protected void demoteOther()
            throws BigDBException, UnsupportedEncodingException {
        Query query = Query.parse("/core/aaa/authorizer[name=\"other\"]");
        String replaceData = "{"
                +   "\"priority\":200,"
                +   "\"name\": \"other\""
                + "}";
        InputStream input = new ByteArrayInputStream(replaceData.getBytes("UTF-8"));
        treespace.replaceData(query, Treespace.DataFormat.JSON, input,
                              AuthContext.SYSTEM);
    }

    protected void disableOther()
            throws BigDBException, UnsupportedEncodingException {
        Query query;

        Query.Builder builder = Query.builder();
        builder.setBasePath("/core/aaa/authorizer[name=\"other\"]");
        query = builder.getQuery();

        treespace.deleteData(query, AuthContext.SYSTEM);
    }

    // make the 'other' authorizer come before 'dummy'
    protected void promoteOther()
            throws BigDBException, UnsupportedEncodingException {
        Query query = Query.parse("/core/aaa/authorizer[name=\"other\"]");
        String replaceData = "{"
                +   "\"priority\":80,"
                +   "\"name\": \"other\""
                + "}";
        InputStream input = new ByteArrayInputStream(replaceData.getBytes("UTF-8"));
        treespace.replaceData(query, Treespace.DataFormat.JSON, input,
                              AuthContext.SYSTEM);
    }

    @Test
    public void testAuthenticateLocalSuccess() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest("admin", "admin"), null);
        assertTrue(result.isSuccess());
        assertTrue(result.getUser().getUser().equals("admin"));
        assertTrue(result.getUser().getFullName().equals("Ed Ming"));
        assertTrue(result.getUser().isAdmin());
    }

    @Test
    public void testAuthenticateLocalWrongPasswd() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest("admin", "nimda"), null);
        assertTrue(result.getStatus()==Status.CLIENT_ERROR_UNAUTHORIZED);
    }

    @Test
    public void testAuthenticateDummySuccess() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest("santa", "hoho"), null);
        assertTrue(result.isSuccess());
        assertTrue(result.getUser().getUser().equals("santa"));
        assertTrue(result.getUser().getFullName().equals("Santa Claus"));
        assertTrue(result.getUser().isAdmin());
        assertFalse(result.getUser().getGroups().contains("other"));
    }

    @Test
    public void testAuthenticateOtherSuccess() throws Exception {

        BigDBGroup other = new BigDBGroup("other");

        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest("santa", "hoho"), null);
        assertTrue(result.isSuccess());
        assertTrue(result.getUser().getUser().equals("santa"));
        assertTrue(result.getUser().getFullName().equals("Santa Claus"));
        assertTrue(result.getUser().isAdmin());
        assertFalse(result.getUser().getGroups().contains(other));

        promoteOther();

        result = authenticator.authenticate(new LoginRequest("santa", "hoho"), null);
        assertTrue(result.isSuccess());
        assertTrue(result.getUser().getGroups().contains(other));

        disableOther();

        result = authenticator.authenticate(new LoginRequest("santa", "hoho"), null);
        assertTrue(result.isSuccess());
        assertFalse(result.getUser().getGroups().contains(other));

        promoteOther();

        result = authenticator.authenticate(new LoginRequest("santa", "hoho"), null);
        assertTrue(result.isSuccess());
        assertTrue(result.getUser().getGroups().contains(other));

        demoteOther();

        result = authenticator.authenticate(new LoginRequest("santa", "hoho"), null);
        assertTrue(result.isSuccess());
        assertFalse(result.getUser().getGroups().contains(other));

    }

    @Test
    public void testAuthenticateDummyWrongPasswd() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest("santa", "kringle"), null);
        assertTrue(result.getStatus()==Status.CLIENT_ERROR_UNAUTHORIZED);
    }

}
