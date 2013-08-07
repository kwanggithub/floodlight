package org.projectfloodlight.db.auth.simpleacl;

import static org.junit.Assert.assertEquals;
import static org.projectfloodlight.db.auth.BigDBAuthTestUtils.MOCK_AUTH_CONTEXT;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.auth.simpleacl.SimpleACL;
import org.projectfloodlight.db.auth.simpleacl.SimpleACLEntry;
import org.projectfloodlight.db.auth.simpleacl.SimpleACLMatch;
import org.projectfloodlight.db.auth.simpleacl.SimpleACLRecorder;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.hook.AuthorizationHook;
import org.projectfloodlight.db.hook.internal.AuthorizationHookContextImpl;
import org.projectfloodlight.db.query.parser.XPathParserUtils;

public class SimpleACLRecorderTest {

    @Rule
    public TemporaryFolder basePath = new TemporaryFolder();

    @Test
    public void test() throws IOException, BigDBException {
        File tmpFile = basePath.newFile("SimpleACLRecorderTest.acl");

        AuthContext authContext = MOCK_AUTH_CONTEXT;
        SimpleACLRecorder aclRecorder =
                new SimpleACLRecorder(AuthorizationHook.Result.ACCEPT, tmpFile);

        AuthorizationHookContextImpl authHookContext =
                new AuthorizationHookContextImpl(
                        AuthorizationHook.Operation.QUERY, DataNode.NULL,
                        DataNode.NULL, DataNode.NULL, authContext);
        authHookContext.setStage(AuthorizationHook.Stage.AUTHORIZATION);
        LocationPathExpression hookPath1 =
                XPathParserUtils
                        .parseSingleLocationPathExpression("core", null);
        authHookContext.setHookInfo(hookPath1, null, DataNode.NULL,
                DataNode.NULL, DataNode.NULL);
        aclRecorder.authorize(authHookContext);
        LocationPathExpression hookPath2 =
                XPathParserUtils.parseSingleLocationPathExpression(
                        "aaa/list/status", null);
        authHookContext.setHookInfo(hookPath2, null, DataNode.NULL,
                DataNode.NULL, DataNode.NULL);
        aclRecorder.authorize(authHookContext);
        authHookContext =
                new AuthorizationHookContextImpl(
                        AuthorizationHook.Operation.MUTATION, DataNode.NULL,
                        DataNode.NULL, DataNode.NULL, authContext);
        LocationPathExpression hookPath3 =
                XPathParserUtils.parseSingleLocationPathExpression(
                        "a/asdfsadf", null);
        authHookContext.setHookInfo(hookPath3, null, DataNode.NULL,
                DataNode.NULL, DataNode.NULL);
        aclRecorder.authorize(authHookContext);

        aclRecorder.close();

        SimpleACL acl = SimpleACL.fromStream(new FileInputStream(tmpFile));
        assertEquals(3, acl.getEntries().size());
        assertEquals(new SimpleACLEntry(new SimpleACLMatch(
                AuthorizationHook.Operation.QUERY, hookPath1),
                AuthorizationHook.Result.ACCEPT), acl.getEntries().get(0));
        assertEquals(new SimpleACLEntry(new SimpleACLMatch(
                AuthorizationHook.Operation.QUERY, hookPath2),
                AuthorizationHook.Result.ACCEPT), acl.getEntries().get(1));
        assertEquals(new SimpleACLEntry(new SimpleACLMatch(
                AuthorizationHook.Operation.MUTATION, hookPath3),
                AuthorizationHook.Result.ACCEPT), acl.getEntries().get(2));
    }
}
