package org.projectfloodlight.db.auth.password;

import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.NullAuthorizationHook;
import org.projectfloodlight.db.auth.session.SimpleSessionManager;
import org.projectfloodlight.db.rest.BigDBRestAPITestBase;
import org.restlet.data.Method;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Exposes the hash-password pseudo-collection to hash a password. Call with a query of
 *  /api/v1/data/controller/core/aaa/hash-password[password='foobar']

 *  The result looks like this
 *  {
 * "hashed-password" : "method=PBKDF2WithHmacSHA1,salt=5C-RsZoBdw7YH_LvCq9Wmw,rounds=10240,37ROndNx8M1MMhzOJyyxmGmrHYzNm4wxy933GAfmi4A"
 *  }
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class BigDBHashPasswordResourceTest extends BigDBRestAPITestBase {
    protected static Logger logger =
            LoggerFactory.getLogger(BigDBHashPasswordResourceTest.class);

    @BeforeClass
    public static void setupClass() throws Exception {
        dbService = defaultService();
        authConfig = new AuthConfig("enabled=true, enableNullAuthentication=true")
        .setParam(AuthConfig.DEFAULT_QUERY_AUTHORIZATION_HOOK, NullAuthorizationHook.class)
        .setParam(AuthConfig.DEFAULT_QUERY_PREAUTHORIZATION_HOOK, NullAuthorizationHook.class)
        .setParam(AuthConfig.ENABLE_NULL_AUTHENTICATION, true)
        .setParam(AuthConfig.SESSION_MANAGER, SimpleSessionManager.class);
        setupBaseClass();
    }    

    @Test
    public void testHashPassword() throws Exception {
        ClientResource client = 
                new ClientResource(Method.GET, 
                                   REST_URL + "/core/aaa/hash-password[password='foobar']");
        try {
            Representation result = client.get();
            logger.info("result: "+result.getText());
        } finally {
            client.release();
        }
    }
}
