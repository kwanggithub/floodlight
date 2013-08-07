package org.projectfloodlight.db.auth.simpleacl;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.BigDBAuthTestUtils;
import org.projectfloodlight.db.auth.simpleacl.SimpleACLAuthorizationHook;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.hook.AuthorizationHook;
import org.projectfloodlight.db.hook.internal.AuthorizationHookContextImpl;
import org.projectfloodlight.db.query.parser.XPathParserUtils;

public class SimpleACLAuthorizerTest {
    @Test
    public void testLoadInputStream() throws IOException, BigDBException {
        String rules =
                "QUERY controller/status ACCEPT\n"
                        + "MUTATION session/mystatus ACCEPT\n" + "* * REJECT\n";

        SimpleACLAuthorizationHook auth =
                new SimpleACLAuthorizationHook(AuthorizationHook.Result.REJECT);
        auth.load(new ByteArrayInputStream(rules.getBytes()));
        testAuthorize(auth, AuthorizationHook.Result.ACCEPT, "QUERY",
                "controller/status");
        testAuthorize(auth, AuthorizationHook.Result.REJECT, "MUTATION",
                "controller/status");
        testAuthorize(auth, AuthorizationHook.Result.ACCEPT, "MUTATION",
                "session/mystatus");
        testAuthorize(auth, AuthorizationHook.Result.REJECT, "QUERY",
                "some/other/session");
    }

    @Test
    public void testDefaultValue() throws BigDBException {
        for (AuthorizationHook.Decision decision : AuthorizationHook.Decision
                .values()) {
            AuthorizationHook.Result result = AuthorizationHook.Result.of(decision);
            SimpleACLAuthorizationHook authorizationHook =
                    new SimpleACLAuthorizationHook(result);
            testAuthorize(authorizationHook, result, "QUERY", "test/a/b");
            authorizationHook.getAcl().addEntry(
                    AuthorizationHook.Operation.QUERY,
                    LocationPathExpression.parse("test/b/c", null),
                    AuthorizationHook.Result.ACCEPT);
            testAuthorize(authorizationHook, result, "QUERY", "test/a/b");
        }
    }

    private void testAuthorize(AuthorizationHook authorizationHook,
            AuthorizationHook.Result result, String opName, String pathString)
            throws BigDBException {
        AuthorizationHook.Operation operation =
                AuthorizationHook.Operation.valueOf(opName);
        LocationPathExpression hookPath =
                XPathParserUtils.parseSingleLocationPathExpression(pathString,
                        null);
        AuthorizationHookContextImpl authHookContext =
                new AuthorizationHookContextImpl(operation, DataNode.NULL,
                        DataNode.NULL, DataNode.NULL,
                        BigDBAuthTestUtils.MOCK_AUTH_CONTEXT);
        authHookContext.setHookInfo(hookPath, null, DataNode.NULL,
                DataNode.NULL, DataNode.NULL);
        authHookContext.setStage(AuthorizationHook.Stage.AUTHORIZATION);
        assertEquals(result, authorizationHook.authorize(authHookContext));
    }
}
