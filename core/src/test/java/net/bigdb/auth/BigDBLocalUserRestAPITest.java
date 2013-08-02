package net.bigdb.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.BigDBException.Type;
import net.bigdb.auth.session.SimpleSessionManager;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeSet;
import net.bigdb.query.Query;
import net.bigdb.rest.BigDBRestAPITestBase;
import net.bigdb.schema.ValidationException;
import net.bigdb.service.Treespace.DataFormat;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.restlet.data.Method;
import org.restlet.data.Status;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;

/** exercises the Session REST API in BigBD
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class BigDBLocalUserRestAPITest extends BigDBRestAPITestBase {
    protected static Logger logger =
            LoggerFactory.getLogger(BigDBLocalUserRestAPITest.class);

    private long tempMtime = 0;
    protected static AuthenticatedRestClient client;
    public static File tempPasswd;

    @BeforeClass
    public static void testSetup() throws Exception {
        dbService = defaultService();
        tempPasswd = basePath.newFile("passwd.txt");
        authConfig = new AuthConfig("enabled=true")
            .setParam(AuthConfig.SESSION_MANAGER, SimpleSessionManager.class)
            .setParam(AuthConfig.RESET_ADMIN_PASSWORD, "adminadmin")
            .setParam(AuthConfig.PASSWD_FILE, tempPasswd);
        setupBaseClass();

        client = new AuthenticatedRestClient(URI.create(REST_SERVER));
        client.login(BigDBUser.PREDEFINED_ADMIN_NAME, "adminadmin");
    }

    @Before
    public void resetAdminPassword() throws Exception {
        DataNodeSet hashResult = getBigDBTreespace().queryData(
                Query.parse("/core/aaa/hash-password[password=$pw]", "pw", "adminadmin"),
               AuthContext.SYSTEM);


        ImmutableMap<String, String> data = 
                ImmutableMap.of("password", 
                                hashResult.getSingleDataNode()
                                    .getChild("hashed-password").getString());

        getBigDBTreespace().updateData(
                Query.parse("/core/aaa/local-user[user-name=$name]", 
                            "name", BigDBUser.PREDEFINED_ADMIN_NAME),
                DataFormat.JSON, new ByteArrayInputStream(new ObjectMapper()
                        .writeValueAsBytes(data)),
                AuthContext.SYSTEM);
    }

    @After
    public void teardownBase() {
        // don't purge sessions
    }

    @Test
    public void testDeleteAdminSpecifically() throws Exception {
        checkRestRequestFails(Method.DELETE, "/core/aaa/local-user[user-name=\""
                + BigDBUser.PREDEFINED_ADMIN_NAME + "\"]");
    }

    @Test
    public void testDeleteAdminWildcarded() throws Exception {
        checkRestRequestFails(Method.DELETE, "/core/aaa/local-user");
    }

    @Test
    public void testAdminDeleteGroups() throws Exception {
        checkRestRequestFails(Method.DELETE, 
                              "/core/aaa/local-user[user-name=\"" + 
                              BigDBUser.PREDEFINED_ADMIN_NAME + "\"]/group");
    }

    @Test(expected=ValidationException.class)
    public void testAdminPutEmptyGroups() throws Exception {
        getBigDBTreespace().updateData(
                Query.parse("/core/aaa/group[name=$name]/user", 
                            "name", BigDBGroup.ADMIN.getName()),
                            DataFormat.JSON,
                            new ByteArrayInputStream("[]".
                                                     getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);
    }

    @Test(expected=ValidationException.class)
    public void testAdminPutGroupsWithoutAdmin() throws Exception {
        getBigDBTreespace().updateData(
                Query.parse("/core/aaa/group[name=$name]/user", 
                            "name", BigDBGroup.ADMIN.getName()),
                            DataFormat.JSON, 
                            new ByteArrayInputStream("[\"foobar\"]".
                                                     getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);
    }

    public void testAdminPutFullGroups() throws Exception {
        getBigDBTreespace().updateData(
                Query.parse("/core/aaa/local-user[user-name=$name]/group", 
                            "name", BigDBUser.PREDEFINED_ADMIN_NAME),
                            DataFormat.JSON, 
                            new ByteArrayInputStream("[\"admin\", \"foobar\"]".
                                                     getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);
        checkAdminPresent();
    }

    @Test
    public void testAdminChangePassword() throws Exception {
        String data = "{" +
                        "\"user-name\" : \"admin\"," +
                        "\"old-password\": \"adminadmin\"," +
                        "\"new-password\": \"foobar\"" +
                        "}";
        getBigDBTreespace().insertData(
                Query.parse("/core/aaa/change-password-local-user"),
                DataFormat.JSON, new ByteArrayInputStream(data.getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);

        testLogin("admin", "foobar");
    }

    @Test
    public void testAdminChangePasswordRest() throws Exception {
        ImmutableMap<String, String> requestMap = ImmutableMap.of(
                        "user-name", "admin",
                        "old-password", "adminadmin",
                        "new-password", "foobar-test");

        String url = "/core/aaa/change-password-local-user";
        logger.debug("===== testAdminChangePasswordRest");
        client.post(url, new JacksonRepresentation<Map<?,?>>(requestMap));
        testLogin("admin", "foobar-test");
    }

    @Test
    public void testAdminChangePasswordOldWrong() throws Exception {
        String data = "{" +
                "\"user-name\" : \"admin\"," +
                "\"old-password\": \"oldWrong\"," +
                "\"new-password\": \"foobar\"" +
                "}";
        try {
            getBigDBTreespace().insertData(
                Query.parse("/core/aaa/change-password-local-user"),
                DataFormat.JSON, new ByteArrayInputStream(data.getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);
            fail("BigDBException expected");
        } catch(BigDBException e) {
            assertEquals(Type.FORBIDDEN, e.getErrorType());
        }
        // password shouldn't have changed
        testLogin("admin", "adminadmin");
    }

    private void testLogin(String userName, String password) throws Exception {
        logger.info("checking login for "+userName + " / "+password);
        new AuthenticatedRestClient(URI.create(REST_SERVER)).login(userName, password);
    }

    /** write out a new password file, but make sure it's recognized
     * as "new" by the underline mtime test.
     *
     * Note that the File interface specifies that mtime is at 1ms granularity,
     * but UNIX usually specifies 1s.
     *
     * @param buf
     */
    protected void writeNewPasswd(String buf) throws Exception {
        tempMtime = tempPasswd.lastModified();

        while (true) {

            FileWriter fw = new FileWriter(tempPasswd);
            fw.write(buf);
            fw.close();

            long newMtime = tempPasswd.lastModified();
            logger.debug("new mtime is {}", newMtime);
            if (newMtime > tempMtime) {
                tempMtime = newMtime;
                break;
            }

            Thread.sleep(100);
        }

    }

    @Test
    public void testAddPrivileged() throws Exception {

        writeNewPasswd("testAddPrivileged-priv-1:x:100:0:Some Privileged User:/tmp:/bin/bash\n"
                       + "testAddPrivileged-priv-2:x:9999:0:Some Other Privileged User:/tmp:/bin/bash\n");

        try {
            getBigDBTreespace().insertData(
                Query.parse("/core/aaa/local-user"),
                DataFormat.JSON, new ByteArrayInputStream("{\"user-name\" : \"testAddPrivileged-priv-1\"}".getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);
            fail("should have thrown a validation exception");
        } catch (ValidationException e) {}

        try {
            getBigDBTreespace().insertData(
                Query.parse("/core/aaa/local-user"),
                DataFormat.JSON, new ByteArrayInputStream("{\"user-name\" : \"testAddPrivileged-priv-2\"}".getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);
            fail("should have thrown a validation exception");
        } catch (ValidationException e) {}

    }

    @Test
    public void testAddUnprivileged() throws Exception {

        writeNewPasswd("testAddUnprivileged-unpriv-1:x:10000:0:Some Unprivileged User:/tmp:/bin/bash\n"
                       + "testAddUnprivileged-unpriv-2:x:10001:0:Some Other Unprivileged User:/tmp:/bin/bash\n");

        getBigDBTreespace().insertData(
                Query.parse("/core/aaa/local-user"),
                DataFormat.JSON, new ByteArrayInputStream("{\"user-name\" : \"testAddUnprivileged-unpriv-1\"}".getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);
        getBigDBTreespace().insertData(
                Query.parse("/core/aaa/local-user"),
                DataFormat.JSON, new ByteArrayInputStream("{\"user-name\" : \"testAddUnprivileged-unpriv-2\"}".getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);
        getBigDBTreespace().insertData(
                Query.parse("/core/aaa/local-user"),
                DataFormat.JSON, new ByteArrayInputStream("{\"user-name\" : \"testAddUnprivileged-remote-1\"}".getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);

        DataNodeSet res = getBigDBTreespace().queryData(Query.parse("/core/aaa/local-user"), AuthContext.SYSTEM);
        boolean foundAdmin = false;
        boolean foundRemote = false;
        boolean foundUnpriv = false;
        boolean foundUnpriv2 = false;
        for(DataNode node : res) {
            if(node.getChild("user-name").getString().equals("admin")) {
                foundAdmin = true;
            }
            if(node.getChild("user-name").getString().equals("testAddUnprivileged-remote-1")) {
                foundRemote = true;
            }
            if(node.getChild("user-name").getString().equals("testAddUnprivileged-unpriv-1")) {
                foundUnpriv = true;
            }
            if(node.getChild("user-name").getString().equals("testAddUnprivileged-unpriv-2")) {
                foundUnpriv2 = true;
            }
        }
        if(!foundAdmin)
            fail("Failed to locate admin user");
        if(!foundRemote)
            fail("Failed to locate remote user");
        if(!foundUnpriv)
            fail("Failed to locate unprivileged user");
        if(!foundUnpriv2)
            fail("Failed to locate other unprivileged user");

    }

    @Test
    public void testAddReparse() throws Exception {

        writeNewPasswd("testAddReparse-priv-1:x:100:0:Some Privileged User:/tmp:/bin/bash\n");

        try {
            getBigDBTreespace().insertData(
                Query.parse("/core/aaa/local-user"),
                DataFormat.JSON, new ByteArrayInputStream("{\"user-name\" : \"testAddReparse-priv-1\"}".getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);
            fail("should have thrown a validation exception");
        } catch (ValidationException e) {}
        getBigDBTreespace().insertData(
                Query.parse("/core/aaa/local-user"),
                DataFormat.JSON, new ByteArrayInputStream("{\"user-name\" : \"testAddReparse-remote-1\"}".getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);

        writeNewPasswd("testAddReparse-priv-2:x:101:0:Some Other Privileged User:/tmp:/bin/bash\n");

        getBigDBTreespace().insertData(
            Query.parse("/core/aaa/local-user"),
            DataFormat.JSON, new ByteArrayInputStream("{\"user-name\" : \"testAddReparse-remote-2\"}".getBytes(Charsets.UTF_8)),
            AuthContext.SYSTEM);
        try {
            getBigDBTreespace().insertData(
                Query.parse("/core/aaa/local-user"),
                DataFormat.JSON, new ByteArrayInputStream("{\"user-name\" : \"testAddReparse-priv-2\"}".getBytes(Charsets.UTF_8)),
                AuthContext.SYSTEM);
            fail("should have thrown a validation exception");
        } catch (ValidationException e) {}

    }

    private void checkRestRequestFails(Method method, String url) throws Exception {
        try {
            client.delete(url);
            fail("ResourceException (error 400) expected ");
        } catch (ResourceException e) {
            Assert.assertEquals(Status.CLIENT_ERROR_BAD_REQUEST,e.getStatus());
        }
        checkAdminPresent();
    }

    private void checkAdminPresent() throws Exception {
        DataNodeSet res = getBigDBTreespace().queryData(Query.parse("/core/aaa/local-user"), AuthContext.SYSTEM);
        boolean found = false;
        for(DataNode node : res) {
            if(node.getChild("user-name").getString().equals(BigDBUser.PREDEFINED_ADMIN_NAME)) {
                // the admin group always contains admin user and cannot be deleted.
                found = true;
            }
        }
        if(!found)
            fail("Failed to located admin user");
    }

}
