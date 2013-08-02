package net.bigdb.auth;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import net.bigdb.BigDBException;
import net.bigdb.auth.password.PasswordHasherMd5;
import net.bigdb.auth.session.SimpleSessionManager;
import net.bigdb.query.Query;
import net.bigdb.rest.auth.LoginRequest;
import net.bigdb.service.Treespace;
import net.floodlightcontroller.bigdb.IBigDBService;
import net.floodlightcontroller.bigdb.MockBigDBService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;

import org.junit.Before;
import org.junit.Test;
import org.restlet.data.Status;

/** Verify that authentication sources located via floodlight modules
 * are loaded correctly.
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public class AuthenticatorModuleTest {

    protected LocalAuthenticator localAuthenticator;
    protected Authenticator authenticator;
    protected MockBigDBService bigDB;
    protected Treespace treespace;

    @Before
    public void setUp() throws Exception {
        FloodlightModuleContext fmc = new FloodlightModuleContext();

        AuthConfig ac = new AuthConfig();
        ac.setParam(AuthConfig.AUTH_ENABLED, true);
        ac.setParam(AuthConfig.SESSION_MANAGER, SimpleSessionManager.class);
        ac.setParam(AuthConfig.PASSWORD_HASHER, PasswordHasherMd5.class);
        ac.setParam(AuthConfig.RESET_ADMIN_PASSWORD, "abc");

        bigDB = new MockBigDBService();
        bigDB.addModuleSchema("floodlight", "2012-10-22");
        bigDB.addModuleSchema("aaa");
        bigDB.setAuthConfig(ac);
        bigDB.init(fmc);
        bigDB.startUp(fmc);
        fmc.addService(IBigDBService.class, bigDB);

        DummyAuthenticator m = new DummyAuthenticator();
        m.init(fmc);

        authenticator = bigDB.getService().getAuthService().getAuthenticator();

        treespace = bigDB.getControllerTreespace();

        initUsersAndGroups();
        initMethodLists();
    }

    /*
     * XXX roth -- shamelessly copied here
     * @see net.bigdb.auth.LocalAuthenticatorTest#initUsersAndGroups()
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
    }

    protected void initMethodLists()
            throws BigDBException, UnsupportedEncodingException {
        Query query;
        String replaceData;
        InputStream input;

        query = Query.parse("/core/aaa/authenticator");
        replaceData = "["
                + "{"
                +   "\"priority\":100"
                +   "," + "\"name\":\"local\""
                + "}"
                + ","
                + "{"
                +   "\"priority\":200"
                +   "," + "\"name\":\"dummy\""
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
//        assertTrue(result.getUser().isAdmin());
    }

    @Test
    public void testAuthenticateDummyWrongPasswd() throws Exception {
        AuthenticationResult result =
                authenticator.authenticate(new LoginRequest("santa", "kringle"), null);
        assertTrue(result.getStatus()==Status.CLIENT_ERROR_UNAUTHORIZED);
    }

}
