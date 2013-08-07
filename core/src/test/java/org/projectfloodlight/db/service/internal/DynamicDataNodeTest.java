package org.projectfloodlight.db.service.internal;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.auth.AuthorizationException;
import org.projectfloodlight.db.auth.BigDBGroup;
import org.projectfloodlight.db.auth.BigDBUser;
import org.projectfloodlight.db.auth.application.ApplicationContext;
import org.projectfloodlight.db.auth.session.Session;
import org.projectfloodlight.db.auth.session.SimpleSessionManager;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.data.ServerDataSource;
import org.projectfloodlight.db.data.annotation.BigDBDelete;
import org.projectfloodlight.db.data.annotation.BigDBIgnore;
import org.projectfloodlight.db.data.annotation.BigDBInsert;
import org.projectfloodlight.db.data.annotation.BigDBParam;
import org.projectfloodlight.db.data.annotation.BigDBPath;
import org.projectfloodlight.db.data.annotation.BigDBQuery;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.hook.AuthorizationHook;
import org.projectfloodlight.db.hook.FilterHook;
import org.projectfloodlight.db.hook.internal.MockAuthorizationHook;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.db.service.internal.ServiceImpl;
import org.projectfloodlight.db.service.internal.TreespaceImpl;
import org.projectfloodlight.db.test.MapperTest;
import org.projectfloodlight.db.util.Path;
import org.restlet.Request;
import org.restlet.data.Method;

import com.google.common.collect.ImmutableList;

public class DynamicDataNodeTest extends MapperTest {

    ServiceImpl service;
    TreespaceImpl treespace;
    ServerDataSource dataSource;
    TestResource testResource;

    public class Child42Resource {
        String child421;
        int child422;

        Child42Resource(String child421, int child422) {
            this.child421 = child421;
            this.child422 = child422;
        }

        public String getChild421() {
            return child421;
        }

        public int getChild422() {
            return child422;
        }

        void setChild422(int child422) {
            this.child422 = child422;
        }
    }

    public class Child4Resource {
        String child41;
        TreeMap<String, Child42Resource> child42 = new TreeMap<String, Child42Resource>();

        public Child4Resource(String child41, List<Child42Resource> child42) {
            this.child41 = child41;
            for (Child42Resource child: child42) {
                this.child42.put(child.getChild421(), child);
            }
        }

        public String getChild41() {
            return child41;
        }

        @BigDBIgnore
        Child42Resource getChild42Resource(String key) {
            return child42.get(key);
        }

        @BigDBIgnore
        Iterable<Child42Resource> getChild42Resources() {
            return child42.values();
        }

        void addChild42Resource(Child42Resource child42Resource) {
            child42.put(child42Resource.getChild421(), child42Resource);
        }

        void deleteChild42Resource(String key) {
            child42.remove(key);
        }
    }

    public class TestResource {

        int child1;
        List<String> child3;
        TreeMap<String, Child4Resource> child4 =
                new TreeMap<String, Child4Resource>();

        @BigDBQuery
        public TestResource queryTestResource() {
            return this;
        }

        @BigDBQuery
        @BigDBPath("child4")
        @BigDBIgnore
        public Iterable<Child4Resource> queryChild4Resources(
                @BigDBParam("location-path") LocationPathExpression locationPath)
                        throws BigDBException {
            Step step = locationPath.getStep(0);
            String key = step.getExactMatchPredicateString("child41");
            if (key != null) {
                Child4Resource child4Resource = child4.get(key);
                return (child4Resource != null) ? ImmutableList.of(child4Resource) :
                    Collections.<Child4Resource>emptyList();
            }
            return child4.values();
        }

        @BigDBQuery
        @BigDBPath("child4/child42")
        @BigDBIgnore
        public Iterable<Child42Resource> queryChild42Resources(
            @BigDBParam("location-path") LocationPathExpression locationPath)
                    throws BigDBException {
            Step child4Step = locationPath.getStep(0);
            String child4Key = child4Step.getExactMatchPredicateString("child41");
            if (child4Key == null)
                throw new IllegalStateException();
            Step child42Step = locationPath.getStep(1);
            String child42Key = child42Step.getExactMatchPredicateString("child421");
            Child4Resource child4Resource = testResource.getChild4Resource(child4Key);
            if (child4Resource == null)
                return Collections.<Child42Resource>emptyList();
            if (child42Key != null) {
                Child42Resource child42Resource = child4Resource.getChild42Resource(child42Key);
                return (child42Resource != null) ?
                        ImmutableList.of(child42Resource) :
                            Collections.<Child42Resource>emptyList();
            }
            return child4Resource.getChild42Resources();
        }

        @BigDBInsert
        @BigDBPath("child4/child42")
        public void addChild42Resource(
                @BigDBParam("location-path") LocationPathExpression locationPath,
                @BigDBParam("mutation-data") DataNode mutationData)
                        throws BigDBException {

            Step child4Step = locationPath.getStep(0);
            String child4Key = child4Step.getExactMatchPredicateString("child41");
            assertThat(child4Key, notNullValue());

            Child4Resource child4Resource = testResource.getChild4Resource(child4Key);
            if (child4Resource != null) {
                Step child42Step = locationPath.getStep(1);
                String child42Key = child42Step.getExactMatchPredicateString("child421");
                for (DataNode child42Element: mutationData) {
                    // Extract the child42 data from the mutation data
                    DataNode child421DataNode = child42Element.getChild("child421");
                    if (!child421DataNode.isNull())
                        child42Key = child421DataNode.getString();
                    DataNode child422DataNode = child42Element.getChild("child422");
                    int child422Value = (int) child422DataNode.getLong(0);

                    Child42Resource child42Resource = child4Resource.getChild42Resource(child42Key);
                    if (child42Resource != null) {
                        if (!child422DataNode.isNull()) {
                            child42Resource.setChild422(child422Value);
                        }
                    } else {
                        child42Resource = new Child42Resource(child42Key, child422Value);
                        child4Resource.addChild42Resource(child42Resource);
                    }
                }
            }
        }

        @BigDBDelete
        @BigDBPath("child4/child42")
        public void deleteChild42Resource(
                @BigDBParam("location-path") LocationPathExpression locationPath)
                        throws BigDBException {
            Step child4Step = locationPath.getStep(0);
            String child4Key = child4Step.getExactMatchPredicateString("child41");
            assertThat(child4Key, notNullValue());

            Step child42Step = locationPath.getStep(1);
            String child42Key = child42Step.getExactMatchPredicateString("child421");

            Child4Resource child4Resource = testResource.getChild4Resource(child4Key);
            if (child4Resource != null) {
                child4Resource.deleteChild42Resource(child42Key);
            }
        }

        public int getChild1() {
            return child1;
        }

        public List<String> getChild3() {
            return child3;
        }

        @BigDBIgnore
        Child4Resource getChild4Resource(String key) {
            return child4.get(key);
        }

        void setChild1(int child1) {
            this.child1 = child1;
        }

        void setChild3(List<String> child3) {
            this.child3 = child3;
        }

        void addChild4(Child4Resource child4Resource) {
            child4.put(child4Resource.getChild41(), child4Resource);
        }
    }

    @BigDBPath("dynamic-leaf")
    public static class DynamicLeafResource {
        @BigDBQuery
        @BigDBPath("child1")
        public static String getChild1() {
            return "foo";
        }
        @BigDBQuery
        @BigDBPath("child2")
        public static int getChild2() {
            return 42;
        }
        @BigDBQuery
        @BigDBPath("child3")
        public static long getChild3() {
            return 111;
        }
    }

    @Before
    public void setup() throws BigDBException {
        service = new ServiceImpl();
        AuthConfig authConfig = new AuthConfig();
        authConfig.setParam(AuthConfig.AUTH_ENABLED, true);
        // FIXME: validationHook1 fails if we run all the tests and we don't
        // set the session manager explicitly here. Why? Talk to Andi.
        // It tries to instantiate the FileSessionManager and it gets an error
        // that .floodlight-sessions is already locked.
        authConfig.setParam(AuthConfig.SESSION_MANAGER,
                SimpleSessionManager.class);
        service.setAuthConfig(authConfig);
        service.initializeFromResource("/org/projectfloodlight/db/service/internal/DynamicDataNodeTest.yaml");
        treespace = (TreespaceImpl) service.getTreespace("DynamicDataNodeTest");
        dataSource = new ServerDataSource("test", treespace.getSchema());
        dataSource.setTreespace(treespace);
        testResource = new TestResource();
        dataSource.registerDynamicDataHooksFromObject(new Path("/test"), testResource);
        treespace.registerDataSource(dataSource);
    }

    private static AuthContext makeAuthContext(Method method, String resourceUrl,
            BigDBUser user) {
        Session session = new Session(100, "cookie", user);
        session.setLastAddress("1.2.3.4");
        Request request = new Request(method, resourceUrl);
        return AuthContext.forSession(session, request);
    }

    private static AuthContext makeAppAuthContext(Method method, String resourceUrl,
                                                  String AppName) {
        ApplicationContext app = new ApplicationContext(AppName);
        Request request = new Request(method, resourceUrl);
        return AuthContext.forApplication(app, request);
    }

    private void insertData(Query updateQuery, String data,
            AuthContext authContext) throws Exception {
        InputStream inputStream =
                new ByteArrayInputStream(data.getBytes("UTF-8"));
        treespace.insertData(updateQuery, Treespace.DataFormat.JSON,
                inputStream, authContext);
    }

    public void initTestResource1() {
        testResource.setChild1(100);
        testResource.setChild3(ImmutableList.<String>of("foo", "bar"));
        Child4Resource child4Resource = new Child4Resource("abc",
                ImmutableList.<Child42Resource>of(
                        new Child42Resource("baa", 100),
                        new Child42Resource("bbb", 200),
                        new Child42Resource("ccc", 300)));
        testResource.addChild4(child4Resource);
    }

    public void initTestResource2() {
        Child4Resource child4Resource = new Child4Resource("abc",
                ImmutableList.<Child42Resource>of(
                        new Child42Resource("include1", 100),
                        new Child42Resource("exclude2", 200),
                        new Child42Resource("exclude3", 300),
                        new Child42Resource("include4", 400)));
        testResource.addChild4(child4Resource);
        child4Resource = new Child4Resource("def",
                ImmutableList.<Child42Resource>of(
                        new Child42Resource("exclude2", 200),
                        new Child42Resource("exclude3", 300),
                        new Child42Resource("include4", 400)));
        testResource.addChild4(child4Resource);
    }

    @Test
    public void query1() throws Exception {
        initTestResource1();
        DataNodeSet dataNodeSet = treespace.queryData(Query.ROOT_QUERY, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet.getSingleDataNode(), "DynamicDataNode.Query1");
    }

    @Test
    public void insertOne() throws Exception {
        initTestResource1();
        Query updateQuery = Query.parse("/test/child4[child41=\"abc\"]/child42");
        String data = "[{\"child421\": \"ddd\", \"child422\": 400}]";
        insertData(updateQuery, data, AuthContext.SYSTEM);
        DataNodeSet dataNodeSet = treespace.queryData(Query.ROOT_QUERY, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet.getSingleDataNode(), "DynamicDataNode.InsertOne");
    }

    @Test
    public void insertMultiple() throws Exception {
        initTestResource1();
        Query updateQuery = Query.parse("/test/child4[child41=\"abc\"]/child42");
        String data = "[{\"child421\": \"ddd\", \"child422\": 400}, {\"child421\": \"eee\", \"child422\": 500}]";
        insertData(updateQuery, data, AuthContext.SYSTEM);
        DataNodeSet dataNodeSet = treespace.queryData(Query.ROOT_QUERY, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet.getSingleDataNode(), "DynamicDataNode.InsertMultiple");
    }

    @Test
    @Ignore("Needs some improvements to BigDB core to distinguish between updates and inserts")
    public void updateOne() throws Exception {
        initTestResource1();
        Query updateQuery = Query.parse("/test/child4[child41=\"abc\"]/child42[child421=\"bbb\"]");
        String data = "{\"child422\": 600}";
        insertData(updateQuery, data, AuthContext.SYSTEM);
        DataNodeSet dataNodeSet = treespace.queryData(Query.ROOT_QUERY, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet.getSingleDataNode(), "DynamicDataNode.UpdateOne");
    }

    @Test
    public void deleteOne() throws Exception {
        initTestResource1();
        Query deleteQuery = Query.parse("/test/child4[child41=\"abc\"]/child42[child421=\"bbb\"]");
        treespace.deleteData(deleteQuery, AuthContext.SYSTEM);
        DataNodeSet dataNodeSet = treespace.queryData(Query.ROOT_QUERY, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet.getSingleDataNode(), "DynamicDataNode.DeleteOne");
    }

    @Test
    public void deleteMultiple() throws Exception {
        initTestResource1();
        Query deleteQuery = Query.parse("/test/child4[child41=\"abc\"]/child42[starts-with(child421,\"b\")]");
        treespace.deleteData(deleteQuery, AuthContext.SYSTEM);
        DataNodeSet dataNodeSet = treespace.queryData(Query.ROOT_QUERY, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet.getSingleDataNode(), "DynamicDataNode.DeleteMultiple");
    }

    @Test
    public void deleteAll() throws Exception {
        initTestResource1();
        Query deleteQuery = Query.parse("/test/child4[child41=\"abc\"]/child42");
        treespace.deleteData(deleteQuery, AuthContext.SYSTEM);
        DataNodeSet dataNodeSet = treespace.queryData(Query.ROOT_QUERY, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet.getSingleDataNode(), "DynamicDataNode.DeleteAll");
    }

    public static class Child42FilterHook implements FilterHook {
        @Override
        public Result filter(Context context) throws BigDBException {
            DataNode hookDataNode = context.getHookDataNode();
            String key = hookDataNode.getChild("child421").getString();
            assertThat(key, notNullValue());
            return key.contains("exclude") ? Result.EXCLUDE : Result.INCLUDE;
        }
    }

    @Test
    public void queryWithFilterHook() throws Exception {
        initTestResource2();
        LocationPathExpression filterHookPath =
                LocationPathExpression.parse("/test/child4/child42");
        Child42FilterHook filterHook = new Child42FilterHook();
        treespace.getHookRegistry().registerFilterHook(filterHookPath, filterHook);
        Query query = Query.parse("/test/child4[child41=\"abc\"]/child42");
        DataNodeSet dataNodeSet = treespace.queryData(query, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet, "DynamicDataNode.QueryWithFilterHook");
    }

    @Test
    public void wildcardQueryWithFilterHook() throws Exception {
        initTestResource2();
        LocationPathExpression filterHookPath =
                LocationPathExpression.parse("/test/child4/child42");
        Child42FilterHook filterHook = new Child42FilterHook();
        treespace.getHookRegistry().registerFilterHook(filterHookPath, filterHook);
        Query query = Query.parse("/test/child4");
        DataNodeSet dataNodeSet = treespace.queryData(query, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet, "DynamicDataNode.WildcardQueryWithFilterHook");
    }

    @Test
    public void deleteOneIncludedWithFilterHook() throws Exception {
        initTestResource2();
        LocationPathExpression filterHookPath =
                LocationPathExpression.parse("/test/child4/child42");
        Child42FilterHook filterHook = new Child42FilterHook();
        treespace.getHookRegistry().registerFilterHook(filterHookPath, filterHook);
        Query deleteQuery = Query.parse("/test/child4[child41=\"abc\"]/child42[child421=\"include1\"]");
        // Delete a child42 element that isn't excluded
        treespace.deleteData(deleteQuery, AuthContext.SYSTEM);
        // Unregister the filter hook to make sure we didn't delete the
        // elements that were filtered. If we left the filter hook registered
        // then we couldn't tell the difference between them being deleted
        // and being filtered.
        treespace.getHookRegistry().unregisterFilterHook(filterHookPath, filterHook);
        Query query = Query.parse("/test/child4[child41=\"abc\"]/child42");
        DataNodeSet dataNodeSet = treespace.queryData(query, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet, "DynamicDataNode.DeleteOneIncludedWithFilterHook");
    }

    @Test
    public void deleteOneExcludedWithFilterHook() throws Exception {
        initTestResource2();
        LocationPathExpression filterHookPath =
                LocationPathExpression.parse("/test/child4/child42");
        Child42FilterHook filterHook = new Child42FilterHook();
        treespace.getHookRegistry().registerFilterHook(filterHookPath, filterHook);
        Query deleteQuery = Query.parse("/test/child4[child41=\"abc\"]/child42[child421=\"exclude2\"]");
        // Delete a child42 element that isn't excluded
        treespace.deleteData(deleteQuery, AuthContext.SYSTEM);
        // Unregister the filter hook to make sure we didn't delete the
        // elements that were filtered. If we left the filter hook registered
        // then we couldn't tell the difference between them being deleted
        // and being filtered.
        treespace.getHookRegistry().unregisterFilterHook(filterHookPath, filterHook);
        Query query = Query.parse("/test/child4[child41=\"abc\"]/child42");
        DataNodeSet dataNodeSet = treespace.queryData(query, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet, "DynamicDataNode.DeleteOneExcludedWithFilterHook");
    }

    @Test
    public void deleteMultipleWithFilterHook() throws Exception {
        initTestResource2();
        LocationPathExpression filterHookPath =
                LocationPathExpression.parse("/test/child4/child42");
        Child42FilterHook filterHook = new Child42FilterHook();
        treespace.getHookRegistry().registerFilterHook(filterHookPath, filterHook);
        Query query = Query.parse("/test/child4[child41=\"abc\"]/child42");
        // Delete all child42 elements that aren't filtered
        treespace.deleteData(query, AuthContext.SYSTEM);
        // Unregister the filter hook to make sure we didn't delete the
        // elements that were filtered. If we left the filter hook registered
        // then we couldn't tell the difference between them being deleted
        // and being filtered.
        treespace.getHookRegistry().unregisterFilterHook(filterHookPath, filterHook);
        DataNodeSet dataNodeSet = treespace.queryData(query, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet, "DynamicDataNode.DeleteMultipleWithFilterHook");
    }

    @Test
    public void wildcardDeleteMultipleWithFilterHook() throws Exception {
        initTestResource2();
        LocationPathExpression filterHookPath =
                LocationPathExpression.parse("/test/child4/child42");
        Child42FilterHook filterHook = new Child42FilterHook();
        treespace.getHookRegistry().registerFilterHook(filterHookPath, filterHook);
        Query deleteQuery = Query.parse("/test/child4/child42");
        // Delete all child42 elements that aren't filtered
        treespace.deleteData(deleteQuery, AuthContext.SYSTEM);
        // Unregister the filter hook to make sure we didn't delete the
        // elements that were filtered. If we left the filter hook registered
        // then we couldn't tell the difference between them being deleted
        // and being filtered.
        treespace.getHookRegistry().unregisterFilterHook(filterHookPath, filterHook);
        Query query = Query.parse("/test/child4");
        DataNodeSet dataNodeSet = treespace.queryData(query, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet, "DynamicDataNode.WildcardDeleteMultipleWithFilterHook");
    }

    @Test
    public void unauthorizedDelete() throws Exception {
        initTestResource1();
        BigDBUser restrictedUser = new BigDBUser("alice", "Alice Smith",
                Collections.<BigDBGroup>emptySet());
        AuthContext authContext = makeAuthContext(Method.DELETE, "/test/child4",
                restrictedUser);
        try {
            // Attempt to delete all child4 elements, but not authorized
            Query deleteQuery = Query.parse("/test/child4/child42");
            treespace.deleteData(deleteQuery, authContext);
            fail("Expected authorization exception");
        }
        catch (AuthorizationException e) {
            // Expected failure, so do nothing
        }
    }

    @Test
    public void unauthorizedAppDelete() throws Exception {
        initTestResource1();
        AuthContext authContext = makeAppAuthContext(Method.DELETE, "/test/child4",
                "some-app");
        try {
            // Attempt to delete all child4 elements, but not authorized
            Query deleteQuery = Query.parse("/test/child4/child42");
            treespace.deleteData(deleteQuery, authContext);
            fail("Expected authorization exception");
        }
        catch (AuthorizationException e) {
            // Expected failure, so do nothing
        }
    }

    @Test
    public void authorizedDelete() throws Exception {
        initTestResource1();
        BigDBUser restrictedUser = new BigDBUser("alice", "Alice Smith",
                Collections.<BigDBGroup>emptySet());
        AuthContext authContext = makeAuthContext(Method.DELETE, "/test/child4",
                restrictedUser);
        LocationPathExpression authHookPath = LocationPathExpression.parse("/test/child4/child42");
        MockAuthorizationHook authHook =
                new MockAuthorizationHook(AuthorizationHook.Result.ACCEPT);
        treespace.getHookRegistry().registerAuthorizationHook(authHookPath,
                AuthorizationHook.Operation.MUTATION,
                AuthorizationHook.Stage.AUTHORIZATION, true, authHook);
            // Attempt to delete all child4 elements, but not authorized
        Query deleteQuery = Query.parse("/test/child4/child42");
        treespace.deleteData(deleteQuery, authContext);

        // Check that our mock auth hook was actually invoked, i.e. that it
        // was the one that made the decision to authorize the operation and
        // not something else.
        // Note: Currently the auth hook we installed at the list level is still
        // called once for each list element result. That's because we're
        // querying for those list elements so each list element is a separate
        // result in the Iterable<DataNodeWithPath> that's returned from the
        // queryWithPath call. This is sort of an artifact of the current kludgy
        // way we're performing mutations for dynamic data. If we clean up that
        // code then the auth hook would only be invoked once, so this
        // assertion would change.
        assertThat(authHook.getAuthorizeCallCounter(), is(3));
    }

    @Test
    public void authorizedAppDelete() throws Exception {
        initTestResource1();
        AuthContext authContext = makeAppAuthContext(Method.DELETE, "/test/child4",
                "some-app");
        LocationPathExpression authHookPath = LocationPathExpression.parse("/test/child4/child42");
        MockAuthorizationHook authHook =
                new MockAuthorizationHook(AuthorizationHook.Result.ACCEPT);
        treespace.getHookRegistry().registerAuthorizationHook(authHookPath,
                AuthorizationHook.Operation.MUTATION,
                AuthorizationHook.Stage.AUTHORIZATION, true, authHook);
            // Attempt to delete all child4 elements, but not authorized
        Query deleteQuery = Query.parse("/test/child4/child42");
        treespace.deleteData(deleteQuery, authContext);

        // Check that our mock auth hook was actually invoked, i.e. that it
        // was the one that made the decision to authorize the operation and
        // not something else.
        // Note: Currently the auth hook we installed at the list level is still
        // called once for each list element result. That's because we're
        // querying for those list elements so each list element is a separate
        // result in the Iterable<DataNodeWithPath> that's returned from the
        // queryWithPath call. This is sort of an artifact of the current kludgy
        // way we're performing mutations for dynamic data. If we clean up that
        // code then the auth hook would only be invoked once, so this
        // assertion would change.
        assertThat(authHook.getAuthorizeCallCounter(), is(3));
    }

    @Test
    public void dynamicLeafData() throws Exception {
        dataSource.registerDynamicDataHooksFromObject(Path.ROOT_PATH, new DynamicLeafResource());
        Query query = Query.parse("/dynamic-leaf");
        DataNodeSet dataNodeSet = treespace.queryData(query, AuthContext.SYSTEM);
        checkExpectedResult(dataNodeSet, "DynamicDataNode.DynamicLeafData");
    }
}
