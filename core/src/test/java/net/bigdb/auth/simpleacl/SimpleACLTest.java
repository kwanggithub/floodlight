package net.bigdb.auth.simpleacl;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import net.bigdb.BigDBException;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.AuthorizationHook;

import org.junit.Test;

public class SimpleACLTest {
    @Test
    public void testInOut() throws IOException, BigDBException {
        String input =
                "QUERY controller/status ACCEPT\n"
                        + "MUTATION restricted/whitelist ACCEPT\n"
                        + "QUERY * ACCEPT\n" + "* * ACCEPT\n";
        InputStream in = new ByteArrayInputStream(input.getBytes());
        SimpleACL acl = SimpleACL.fromStream(in);

        assertEquals(4, acl.getEntries().size());

        assertEquals(new SimpleACLEntry(new SimpleACLMatch(
                AuthorizationHook.Operation.QUERY, LocationPathExpression
                        .parse("controller/status", null)),
                AuthorizationHook.Result.ACCEPT), acl.getEntries().get(0));

        assertEquals(new SimpleACLEntry(new SimpleACLMatch(
                AuthorizationHook.Operation.MUTATION, LocationPathExpression
                        .parse("restricted/whitelist", null)),
                AuthorizationHook.Result.ACCEPT), acl.getEntries().get(1));

        assertEquals(new SimpleACLEntry(new SimpleACLMatch(
                AuthorizationHook.Operation.QUERY, null),
                AuthorizationHook.Result.ACCEPT), acl.getEntries().get(2));

        assertEquals(new SimpleACLEntry(new SimpleACLMatch(null, null),
                AuthorizationHook.Result.ACCEPT), acl.getEntries().get(3));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        acl.writeTo(out);
        assertEquals(input, new String(out.toByteArray()));
    }
}
