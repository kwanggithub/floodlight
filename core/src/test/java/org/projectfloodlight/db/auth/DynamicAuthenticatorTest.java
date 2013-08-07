package org.projectfloodlight.db.auth;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.IBigDBService;
import org.projectfloodlight.db.MockBigDBService;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.auth.AuthenticationResult;
import org.projectfloodlight.db.auth.AuthenticatorMethod;
import org.projectfloodlight.db.auth.BigDBGroup;
import org.projectfloodlight.db.auth.DummyAuthenticator;
import org.projectfloodlight.db.auth.DynamicAuthenticator;
import org.projectfloodlight.db.auth.LocalAuthenticator;
import org.projectfloodlight.db.auth.LocalGroupValidationHook;
import org.projectfloodlight.db.auth.DummyAuthenticator.Method;
import org.projectfloodlight.db.auth.password.PasswordHasherMd5;
import org.projectfloodlight.db.auth.session.SimpleSessionManager;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.rest.auth.LoginRequest;
import org.projectfloodlight.db.schema.ValidationException;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.db.service.Treespace.DataFormat;
import org.restlet.data.Status;

public class DynamicAuthenticatorTest {

    protected LocalAuthenticator localAuthenticator;
    protected Method dummyAuthenticator;
    protected DynamicAuthenticator authenticator;
    protected MockBigDBService bigDB;
    protected Treespace treespace;

    public class TestRegistry implements AuthenticatorMethod.Registry {
        private Iterable<AuthenticatorMethod> getMethods() {
            LinkedList<AuthenticatorMethod> l = new LinkedList<AuthenticatorMethod>();
            l.add(dummyAuthenticator);
            l.add(localAuthenticator);
            return l;
        }
        @Override
        public Iterable<String> getMethodNames() {
            LinkedList<String> methodNames = new LinkedList<String>();
            methodNames.add(DummyAuthenticator.Method.NAME);
            methodNames.add(LocalAuthenticator.NAME);
            return methodNames;
        }
        @Override
        public Iterable<AuthenticatorMethod> getAuthnMethods() {
            return getMethods();
        }
        @Override
        public Iterable<AuthenticatorMethod> getAuthzMethods() {
            return getMethods();
        }
    }

    @Before
    public void setUp() throws Exception {
        FloodlightModuleContext fmc = new FloodlightModuleContext();

        AuthConfig ac = new AuthConfig();
        ac.setParam(AuthConfig.AUTH_ENABLED, true);
        ac.setParam(AuthConfig.SESSION_MANAGER, SimpleSessionManager.class);
        ac.setParam(AuthConfig.RESET_ADMIN_PASSWORD, "test");

        bigDB = new MockBigDBService();
        bigDB.addModuleSchema("floodlight", "2012-10-22");
        bigDB.addModuleSchema("aaa");
        bigDB.setAuthConfig(ac);
        bigDB.init(fmc);
        bigDB.startUp(fmc);
        fmc.addService(IBigDBService.class, bigDB);

        localAuthenticator = new LocalAuthenticator(bigDB.getService(), new PasswordHasherMd5());
        dummyAuthenticator = new Method();

        /* all authenticators should have auto-registered by now */
        authenticator = new DynamicAuthenticator(bigDB.getService(), localAuthenticator,
                                                 new TestRegistry());

        treespace = bigDB.getControllerTreespace();
        treespace.getHookRegistry().registerValidationHook(
                       LocationPathExpression.parse("/core/aaa/group"), true,
                       new LocalGroupValidationHook(treespace));
        initUsersAndGroups();
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
//                "\"group\":[\"admin\", \"reader\"]" +
                "}, {" +
                "\"user-name\":\"goofy\"," +
                "\"password\": \"\"," +
                "\"full-name\":\"Go Ofy\"" +
//                "\"group\":[\"goofer\"]" +
                "}, {" +
                "\"user-name\":\"no-full-name\"," +
                "\"password\": \"\"" +  // user doesn't have full name set
                "}]";
        input = new ByteArrayInputStream(replaceData.getBytes("UTF-8"));
        treespace.replaceData(query, Treespace.DataFormat.JSON, input,
                              AuthContext.SYSTEM);
        query = Query.parse("/core/aaa/group");
        String groupJson = null;
        groupJson = "{" +
                "\"name\":\"sample-group-1\"," +
                "\"user\":[\"admin\", \"goofy\"]" +
                "}";
        input = new ByteArrayInputStream(groupJson.getBytes("UTF-8"));
        treespace.insertData(query, Treespace.DataFormat.JSON, input,
                              AuthContext.SYSTEM);
        groupJson = "{\"name\":\"sample-group-2\"}, " +
                     "{\"name\":\"goofer\", \"user\":[\"goofy\"]}";
        input = new ByteArrayInputStream(groupJson.getBytes("UTF-8"));
        treespace.insertData(query, Treespace.DataFormat.JSON, input,
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
    }

    @Test
    public void testAuthenticateDummyWrongPasswd() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest("santa", "kringle"), null);
        assertTrue(result.getStatus()==Status.CLIENT_ERROR_UNAUTHORIZED);
    }

    @Test
    public void testUserGroups() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest("goofy", ""), null);

        assertTrue(result.isSuccess());
        Set<BigDBGroup> groups = result.getUser().getGroups();
        assertTrue(groups.contains(new BigDBGroup("sample-group-1")));
        // group deletion
        String groupPath = "/core/aaa/group[name=$group-name]";
        Query query = Query.parse(groupPath, "group-name", "goofer");
        treespace.deleteData(query, AuthContext.SYSTEM);
        query = Query.parse(groupPath, "group-name", "sample-group-1");
        treespace.deleteData(query, AuthContext.SYSTEM);
        query = Query.parse(groupPath, "group-name", "sample-group-2");
        treespace.deleteData(query, AuthContext.SYSTEM);
        query = Query.parse(groupPath, "group-name", BigDBGroup.ADMIN.getName());
        try {
            treespace.deleteData(query, AuthContext.SYSTEM);
            fail("Admin group should not be deleted.");
        } catch (Exception e) {
            assertTrue(e instanceof ValidationException);
        }
        query = Query.builder().setBasePath("/core/aaa/group[name=$group-name]/user").
                setVariable("group-name", BigDBGroup.ADMIN.getName()).getQuery();
        String replaceData = "[\"user-1\", \"user-2\"]";
        InputStream input = new ByteArrayInputStream(replaceData.getBytes("UTF-8"));
        try {
            treespace.replaceData(query, DataFormat.JSON, input, AuthContext.SYSTEM);
            fail("Admin user in admin group should not be deleted.");
        } catch (Exception e) {
            assertTrue(e instanceof ValidationException);
        }
    }
}
