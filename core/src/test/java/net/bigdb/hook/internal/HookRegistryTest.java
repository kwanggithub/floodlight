package net.bigdb.hook.internal;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.AuthorizationHook;
import net.bigdb.hook.FilterHook;
import net.bigdb.query.parser.XPathParserUtils;
import net.bigdb.schema.ContainerSchemaNode;
import net.bigdb.schema.ModuleIdentifier;
import net.bigdb.schema.Schema;
import net.bigdb.schema.internal.SchemaImpl;
import net.bigdb.service.internal.TreespaceImpl;

import org.junit.Test;

public class HookRegistryTest {

    private HookRegistryImpl buildHookRegistry() throws BigDBException {
        ModuleIdentifier moduleId = new ModuleIdentifier("TestModule");
        ContainerSchemaNode root = new ContainerSchemaNode("", moduleId);
        ContainerSchemaNode container1 =
                new ContainerSchemaNode("Container1", moduleId);
        root.addChildNode("Container1", container1);
        ContainerSchemaNode container2 =
                new ContainerSchemaNode("Container2", moduleId);
        root.addChildNode("Container2", container2);
        ContainerSchemaNode container3 =
                new ContainerSchemaNode("Container3", moduleId);
        container2.addChildNode("Container3", container3);
        Schema schema = new SchemaImpl(root);
        TreespaceImpl mockTreespace = createNiceMock(TreespaceImpl.class);
        expect(mockTreespace.getSchema()).andReturn(schema).anyTimes();
        mockTreespace.authorize(null, null, null, null);
        replay(mockTreespace);
        HookRegistryImpl hookRegistry = new HookRegistryImpl();
        return hookRegistry;
    }

    @Test
    public void basicFilterHook() throws Exception {
        // Create the hook registry with a test schema/treespace
        HookRegistryImpl hookRegistry = buildHookRegistry();

        // Register a filter hook
        FilterHook filter1 = createMock(FilterHook.class);
        LocationPathExpression path =
                XPathParserUtils.parseSingleLocationPathExpression(
                        "/Container1", null);
        hookRegistry.registerFilterHook(path, filter1);
        List<FilterHook> filterHooks = hookRegistry.getFilterHooks(path);
        assertEquals(1, filterHooks.size());
        assertTrue(filterHooks.contains(filter1));

        // Register another hook
        FilterHook filter2 = createMock(FilterHook.class);
        hookRegistry.registerFilterHook(path, filter2);
        filterHooks = hookRegistry.getFilterHooks(path);
        assertEquals(2, filterHooks.size());
        assertTrue(filterHooks.contains(filter1));
        assertTrue(filterHooks.contains(filter2));

        // Unregister the first hook
        hookRegistry.unregisterFilterHook(path, filter1);
        filterHooks = hookRegistry.getFilterHooks(path);
        assertEquals(1, filterHooks.size());
        assertTrue(filterHooks.contains(filter2));

        // Unregister the second hook
        hookRegistry.unregisterFilterHook(path, filter2);
        filterHooks = hookRegistry.getFilterHooks(path);
        assertEquals(0, filterHooks.size());
    }

    @Test
    public void multipleFilterHooks() throws Exception {
        // Create the hook registry with a test schema/treespace
        HookRegistryImpl hookRegistry = buildHookRegistry();

        // Register a filter at first schema node
        FilterHook filter1 = createMock(FilterHook.class);
        LocationPathExpression path1 =
                XPathParserUtils.parseSingleLocationPathExpression(
                        "/Container1", null);
        hookRegistry.registerFilterHook(path1, filter1);

        // Register a second filter at second schema node
        FilterHook filter2 = createMock(FilterHook.class);
        LocationPathExpression path2 =
                XPathParserUtils.parseSingleLocationPathExpression(
                        "/Container2", null);
        hookRegistry.registerFilterHook(path2, filter2);

        // Register a third filter at third schema node
        FilterHook filter3 = createMock(FilterHook.class);
        LocationPathExpression path3 =
                XPathParserUtils.parseSingleLocationPathExpression(
                        "/Container2/Container3", null);
        hookRegistry.registerFilterHook(path3, filter3);

        // Verify that the schema nodes have the correct hooks registered
        List<FilterHook> filterHooks = hookRegistry.getFilterHooks(path1);
        assertEquals(1, filterHooks.size());
        assertTrue(filterHooks.contains(filter1));

        filterHooks = hookRegistry.getFilterHooks(path2);
        assertEquals(1, filterHooks.size());
        assertTrue(filterHooks.contains(filter2));

        filterHooks = hookRegistry.getFilterHooks(path3);
        assertEquals(1, filterHooks.size());
        assertTrue(filterHooks.contains(filter3));
    }

    @Test
    public void basicAuthorizationHook() throws Exception {
        // Create the hook registry with a test schema/treespace
        HookRegistryImpl hookRegistry = buildHookRegistry();

        // Register a authorization hook
        AuthorizationHook auth1 = createMock(AuthorizationHook.class);
        LocationPathExpression path =
                XPathParserUtils.parseSingleLocationPathExpression(
                        "/Container2", null);
        hookRegistry.registerAuthorizationHook(path,
                AuthorizationHook.Operation.MUTATION,
                AuthorizationHook.Stage.AUTHORIZATION, false, auth1);
        List<AuthorizationHook> authHooks =
                hookRegistry.getAuthorizationHooks(path,
                        AuthorizationHook.Operation.MUTATION,
                        AuthorizationHook.Stage.AUTHORIZATION, false);
        assertEquals(1, authHooks.size());
        assertTrue(authHooks.contains(auth1));

        // Unregister it
        hookRegistry.unregisterAuthorizationHook(path,
                AuthorizationHook.Operation.MUTATION,
                AuthorizationHook.Stage.AUTHORIZATION, false, auth1);
        authHooks =
                hookRegistry.getAuthorizationHooks(path,
                        AuthorizationHook.Operation.MUTATION,
                        AuthorizationHook.Stage.AUTHORIZATION, false);
        assertEquals(0, authHooks.size());
    }

    @Test
    public void unregisterUnregisteredHook() throws Exception {
        // Create the hook registry with a test schema/treespace
        HookRegistryImpl hookRegistry = buildHookRegistry();

        // Unregister an auth hook before it's been registered.
        // This should be a no-op, i.e. shouldn't throw exception
        AuthorizationHook auth1 = createMock(AuthorizationHook.class);
        LocationPathExpression path =
                XPathParserUtils.parseSingleLocationPathExpression(
                        "/Container2", null);
        hookRegistry.unregisterAuthorizationHook(path,
                AuthorizationHook.Operation.MUTATION,
                AuthorizationHook.Stage.AUTHORIZATION, false, auth1);

        // Verify that there's nothing registered
        List<AuthorizationHook> authHooks =
                hookRegistry.getAuthorizationHooks(path,
                        AuthorizationHook.Operation.MUTATION,
                        AuthorizationHook.Stage.AUTHORIZATION, false);
        assertEquals(0, authHooks.size());
    }
}
