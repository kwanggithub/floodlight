package org.projectfloodlight.db.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;

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
import org.projectfloodlight.db.auth.Authenticator;
import org.projectfloodlight.db.auth.BigDBGroup;
import org.projectfloodlight.db.auth.BigDBUser;
import org.projectfloodlight.db.auth.LocalAuthenticator;
import org.projectfloodlight.db.auth.AuthenticatorMethod.AuthnResult;
import org.projectfloodlight.db.auth.AuthenticatorMethod.AuthzResult;
import org.projectfloodlight.db.auth.password.PasswordHasherMd5;
import org.projectfloodlight.db.auth.session.SimpleSessionManager;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.rest.auth.LoginRequest;
import org.projectfloodlight.db.service.Treespace;
import org.restlet.data.ClientInfo;
import org.restlet.data.Status;

import com.google.common.base.Strings;

public class LocalAuthenticatorTest {

    protected static final String ADMIN_USER_NAME = "admin";
    protected static final String NON_ADMIN_USER_NAME = "goofy";
    protected LocalAuthenticator method;
    protected Authenticator authenticator;
    protected MockBigDBService bigDB;
    protected Treespace treespace;

    public class TestAuth implements Authenticator {

        @Override
        public
                AuthenticationResult
                authenticate(LoginRequest request,
                             ClientInfo clientInfo)
                                                   throws AuthenticationException {
            AuthnResult r1 = method.getPrincipal(request, clientInfo);
            if (!r1.isSuccess())
                return AuthenticationResult.wrongPassword(request.getUser());
            AuthzResult r2 = method.getGroups(r1, request, clientInfo);
            if (!r2.isSuccess())
                return AuthenticationResult.notAuthorized(r1.getPrincipal());
            LinkedList<BigDBGroup> l = new LinkedList<BigDBGroup>();
            for (String s : r2.getGroups()) {
                l.add(new BigDBGroup(s));
            }
            BigDBUser u = new BigDBUser(r1.getPrincipal(), r1.getFullName(), l);
            return AuthenticationResult.success(u);
        }
    }

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

        method = new LocalAuthenticator(bigDB.getService(), new PasswordHasherMd5());
        authenticator = new TestAuth();

        treespace = bigDB.getControllerTreespace();

        initUsersAndGroups();
    }

    protected void initUsersAndGroups()
            throws BigDBException, UnsupportedEncodingException {
        // Add users
        Query query;
        String replaceData;
        InputStream input;

        query = Query.parse("/core/aaa/local-user");
        replaceData = "[{" +
                "\"user-name\":\"" + ADMIN_USER_NAME + "\"," +
                "\"password\": \"21232f297a57a5a743894a0e4a801fc3\"," +
                "\"full-name\":\"Ed Ming\"" +
                "}, {" +
                "\"user-name\":\"" + NON_ADMIN_USER_NAME + "\"," +
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

    @Test
    public void testAuthenticateSuccess() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest(ADMIN_USER_NAME, ADMIN_USER_NAME), null);
        assertTrue(result.isSuccess());
        assertTrue(result.getUser().getUser().equals(ADMIN_USER_NAME));
        assertTrue(result.getUser().getFullName().equals("Ed Ming"));
        assertTrue(result.getUser().isAdmin());
    }

    @Test
    public void testAuthenticateNoPasswd() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest(NON_ADMIN_USER_NAME, ""), null);
        assertTrue(result.isSuccess());
        assertTrue(result.getUser().getUser().equals(NON_ADMIN_USER_NAME));
        assertTrue(result.getUser().getFullName().equals("Go Ofy"));
        assertFalse(result.getUser().isAdmin());
    }

    @Test
    public void testAuthenticateNoPasswdNoFullName() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest("no-full-name", ""), null);
        assertTrue(result.isSuccess());
        assertTrue(result.getUser().getUser().equals("no-full-name"));
        assertTrue(Strings.isNullOrEmpty(result.getUser().getFullName()));
    }

    @Test
    public void testAuthenticateWrongPasswd() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest(ADMIN_USER_NAME, "nimda"), null);
        assertTrue(result.getStatus()==Status.CLIENT_ERROR_UNAUTHORIZED);
    }

    @Test
    public void testAuthenticateNoUser() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest("nimda", "nimda"), null);
        assertTrue(result.getStatus()==Status.CLIENT_ERROR_UNAUTHORIZED);
    }

    @Test
    public void testResetAdminPassword() throws Exception {
        method.resetAdminPassword("foobar");

        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest(ADMIN_USER_NAME, "foobar"), null);
        assertTrue(result.isSuccess());
        result =
                authenticator.authenticate(new LoginRequest(ADMIN_USER_NAME, ADMIN_USER_NAME), null);
        assertFalse(result.isSuccess());
    }

    @Test
    public void testChangeAdminPasswordViaUserApi() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest(ADMIN_USER_NAME, "admin"), null);
        assertTrue(result.isSuccess());

        String newPassword = "heppohippo\"";
        method.changeUserPassword(ADMIN_USER_NAME, "admin", newPassword);

        result =
                authenticator.authenticate(new LoginRequest(ADMIN_USER_NAME, "admin"), null);
        assertFalse(result.isSuccess());

        result =
                authenticator.authenticate(new LoginRequest(ADMIN_USER_NAME, newPassword), null);
        assertTrue(result.isSuccess());
    }

    @Test
    public void testChangeNonAdminPasswordViaUserApi() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest(NON_ADMIN_USER_NAME, ""), null);
        assertTrue(result.isSuccess());

        String newPassword = "fancyNewPassword";
        method.changeUserPassword(NON_ADMIN_USER_NAME, "", newPassword);

        result =
                authenticator.authenticate(new LoginRequest(NON_ADMIN_USER_NAME, ""), null);
        assertFalse(result.isSuccess());

        result =
                authenticator.authenticate(new LoginRequest(NON_ADMIN_USER_NAME, newPassword), null);
        assertTrue(result.isSuccess());
    }

    @Test(expected=BigDBException.class)
    public void testChangeNonAdminWrongOldPasswordFails() throws Exception {
        method.changeUserPassword(NON_ADMIN_USER_NAME, "wrongOldPassword", "newPassword");
    }

    @Test(expected=BigDBException.class)
    public void testChangeNonExistingUserFails() throws Exception {
        method.changeUserPassword("non-existinguser", "admin", "newPassword");
    }
}
