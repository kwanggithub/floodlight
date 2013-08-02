package net.bigdb.service.internal;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.auth.AuthConfig;
import net.bigdb.auth.AuthContext;
import net.bigdb.auth.AuthorizationException;
import net.bigdb.auth.BigDBAuthTestUtils;
import net.bigdb.auth.BigDBGroup;
import net.bigdb.auth.BigDBUser;
import net.bigdb.auth.session.Session;
import net.bigdb.auth.session.SimpleSessionManager;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeSet;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.AuthorizationHook;
import net.bigdb.hook.FilterHook;
import net.bigdb.hook.ValidationHook;
import net.bigdb.hook.WatchHook;
import net.bigdb.hook.AuthorizationHook.Result;
import net.bigdb.hook.internal.MockAuthorizationHook;
import net.bigdb.query.Query;
import net.bigdb.query.Step;
import net.bigdb.schema.ValidationException;
import net.bigdb.service.Treespace;
import net.bigdb.test.MapperTest;

import static org.hamcrest.CoreMatchers.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.restlet.Request;
import org.restlet.data.Method;

import com.google.common.collect.ImmutableList;

public class HookTest extends MapperTest {

    private ServiceImpl service;
    private TreespaceImpl treespace;
    public final static BigDBUser ADMIN_USER = new BigDBUser("admin",
            "full admin", Collections.singleton(new BigDBGroup("admin")));
    public final static BigDBUser TEST_USER = new BigDBUser("test",
            "full name", Collections.singleton(new BigDBGroup("test")));

    @Before
    public void initializeService() throws BigDBException {
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
        service.initializeFromResource("/net/bigdb/service/internal/HookTest.yaml");
        treespace = (TreespaceImpl) service.getTreespace("HookTest");
    }

    private AuthContext makeAuthContext(Method method, String resourceUrl,
            BigDBUser user) {
        Session session = new Session(100, "cookie", user);
        session.setLastAddress("1.2.3.4");
        Request request = new Request(method, resourceUrl);
        return AuthContext.forSession(session, request);
    }

    private void replaceData(Query query, String data, AuthContext authContext)
            throws Exception {
        InputStream inputStream =
                new ByteArrayInputStream(data.getBytes("UTF-8"));
        treespace.replaceData(query, Treespace.DataFormat.JSON, inputStream,
                authContext);
    }

    private void updateData(Query query, String data, AuthContext authContext)
            throws Exception {
        InputStream inputStream =
                new ByteArrayInputStream(data.getBytes("UTF-8"));
        treespace.updateData(query, Treespace.DataFormat.JSON, inputStream,
                authContext);
    }

    private void insertData(Query query, String data, AuthContext authContext)
            throws Exception {
        InputStream inputStream =
                new ByteArrayInputStream(data.getBytes("UTF-8"));
        treespace.insertData(query, Treespace.DataFormat.JSON, inputStream,
                authContext);
    }

    class TestAuthorizationHook implements AuthorizationHook {

        private Result defaultResult;
        private Map<LocationPathExpression, Result> resultMap =
                new HashMap<LocationPathExpression, Result>();
        private List<LocationPathExpression> expectedPaths =
                new ArrayList<LocationPathExpression>();
        private int callCount = 0;

        public TestAuthorizationHook(Result defaultResult) {
            this.defaultResult = defaultResult;
        }

        public void setResult(LocationPathExpression path, Result result) {
            resultMap.put(path, result);
        }

        public void addExpectedPath(LocationPathExpression hookPath) {
            expectedPaths.add(hookPath);
        }

        @Override
        public Result authorize(Context context) {
            assertTrue(callCount < expectedPaths.size());
            LocationPathExpression hookPath = context.getHookPath();
            assertThat(hookPath, is(expectedPaths.get(callCount)));
            callCount++;
            Result result = resultMap.get(hookPath);
            if (result == null)
                result = defaultResult;
            return result;
        }

        public int getCallCount() {
            return callCount;
        }

        public void resetCallCount() {
            callCount = 0;
        }

        public void checkExpectedCalls() {
            assertThat(callCount, is(expectedPaths.size()));
        }
    }

    @Test
    public void authorizeLeafMutation() throws Exception {
        final String leafPathString = "/hook-test/child1";
        final LocationPathExpression leafPath =
                LocationPathExpression.parse(leafPathString);
        final Query query = Query.parse(leafPathString);
        final TestAuthorizationHook authorizationHook =
                new TestAuthorizationHook(Result.UNDECIDED);
        authorizationHook.setResult(leafPath, Result.REJECT);
        authorizationHook.addExpectedPath(leafPath);
        treespace.getHookRegistry().registerAuthorizationHook(
                leafPath, AuthorizationHook.Operation.MUTATION,
                AuthorizationHook.Stage.AUTHORIZATION, false, authorizationHook);
        BigDBUser testUser =
                new BigDBUser("test", "full test",
                        Collections.<BigDBGroup> emptyList());
        AuthContext authContext =
                makeAuthContext(Method.GET, leafPathString, testUser);
        try {
            replaceData(query, "1000", authContext);
            fail("Expected authorization failure");
        } catch (AuthorizationException e) {
            // Expecting authorization exception
        }

        authorizationHook.resetCallCount();
        replaceData(query, "1000", AuthContext.SYSTEM);
        try {
            // Should still get error even though this doesn't change the config state
            replaceData(query, "1000", authContext);
            fail("Expected authorization failure");
        } catch (AuthorizationException e) {
            // Expecting authorization exception
        }
    }

    @Test
    public void authorizeContainerMutation() throws Exception {
        final String containerPathString = "/hook-test";
        final LocationPathExpression containerPath =
                LocationPathExpression.parse(containerPathString);
        final Query query = Query.parse(containerPathString);
        replaceData(query, "{\"child1\":19,\"child4\":{\"child4.1\":\"def\",\"child4.2\":111},\"child5\":[{\"child5.1\":\"abc\",\"child5.3\":[{\"child5.3.1\":222},{\"child5.3.1\":333}]}]}", AuthContext.SYSTEM);
        final TestAuthorizationHook authorizationHook =
                new TestAuthorizationHook(Result.ACCEPT);
        authorizationHook.addExpectedPath(LocationPathExpression.parse("/hook-test/child1"));
        authorizationHook.addExpectedPath(LocationPathExpression.parse("/hook-test/child4/child4.2"));
        authorizationHook.addExpectedPath(LocationPathExpression.parse("/hook-test/child4"));
        authorizationHook.addExpectedPath(containerPath);
        authorizationHook.addExpectedPath(LocationPathExpression.ROOT_PATH);
        treespace.getHookRegistry().registerAuthorizationHook(
                null, AuthorizationHook.Operation.MUTATION,
                AuthorizationHook.Stage.AUTHORIZATION, false, authorizationHook);
        BigDBUser testUser =
                new BigDBUser("test", "full test",
                        Collections.<BigDBGroup> emptyList());
        AuthContext authContext =
                makeAuthContext(Method.GET, containerPathString, testUser);
        updateData(query, "{\"child1\":22,\"child4\":{\"child4.2\":200}}", authContext);
        authorizationHook.checkExpectedCalls();
    }

    @Test
    public void authorizeListMutation() throws Exception {
        final String listPathString = "/hook-test/child5";
        final LocationPathExpression listPath =
                LocationPathExpression.parse(listPathString);
        final Query query = Query.parse(listPathString);
        final TestAuthorizationHook listAuthorizationHook =
                new TestAuthorizationHook(Result.ACCEPT);
        listAuthorizationHook.addExpectedPath(listPath);
        listAuthorizationHook.addExpectedPath(listPath);
        listAuthorizationHook.addExpectedPath(listPath);
        treespace.getHookRegistry().registerAuthorizationHook(
                listPath, AuthorizationHook.Operation.MUTATION,
                AuthorizationHook.Stage.AUTHORIZATION, true, listAuthorizationHook);
        final TestAuthorizationHook listElementAuthorizationHook =
                new TestAuthorizationHook(Result.ACCEPT);
        listElementAuthorizationHook.addExpectedPath(LocationPathExpression.parse("/hook-test/child5[child5.1=\"abc\"]"));
        listElementAuthorizationHook.addExpectedPath(LocationPathExpression.parse("/hook-test/child5[child5.1=\"def\"]"));
        listElementAuthorizationHook.addExpectedPath(LocationPathExpression.parse("/hook-test/child5[child5.1=\"ghi\"]"));
        listElementAuthorizationHook.addExpectedPath(LocationPathExpression.parse("/hook-test/child5[child5.1=\"abc\"]"));
        treespace.getHookRegistry().registerAuthorizationHook(
                listPath, AuthorizationHook.Operation.MUTATION,
                AuthorizationHook.Stage.AUTHORIZATION, false, listElementAuthorizationHook);
        final TestAuthorizationHook leafAuthorizationHook =
                new TestAuthorizationHook(Result.ACCEPT);
        leafAuthorizationHook.addExpectedPath(LocationPathExpression.parse("/hook-test/child5[child5.1=\"abc\"]/child5.1"));
        leafAuthorizationHook.addExpectedPath(LocationPathExpression.parse("/hook-test/child5[child5.1=\"def\"]/child5.1"));
        leafAuthorizationHook.addExpectedPath(LocationPathExpression.parse("/hook-test/child5[child5.1=\"ghi\"]/child5.1"));
        treespace.getHookRegistry().registerAuthorizationHook(
                LocationPathExpression.parse("/hook-test/child5/child5.1"),
                AuthorizationHook.Operation.MUTATION,
                AuthorizationHook.Stage.AUTHORIZATION, false, leafAuthorizationHook);
        BigDBUser testUser =
                new BigDBUser("test", "full test",
                        Collections.<BigDBGroup> emptyList());
        AuthContext authContext =
                makeAuthContext(Method.GET, "/hook-test", testUser);
        replaceData(query, "[{\"child5.1\":\"abc\",\"child5.2\":33,\"child5.3\":[{\"child5.3.1\":222},{\"child5.3.1\":333}]},{\"child5.1\":\"def\"}]", authContext);
        insertData(query, "[{\"child5.1\":\"ghi\"}]", authContext);
        treespace.deleteData(Query.parse("/hook-test/child5[child5.1=\"abc\"]"), authContext);
        listAuthorizationHook.checkExpectedCalls();
        listElementAuthorizationHook.checkExpectedCalls();
        leafAuthorizationHook.checkExpectedCalls();
    }

    static final String AUTHORIZATION_HOOK_REJECT_MESSAGE = "Mock rejection";

    private void doQueryAuthHookTest(String hookPathString,
            String queryPathString, boolean authorizeList,
            boolean failureExpected) throws Exception {
        String initData = "{\"hook-test\":{\"child1\":100,\"child4\":{\"child4.1\":\"foobar\",\"child4.2\":200}}}";
        replaceData(Query.ROOT_QUERY, initData, AuthContext.SYSTEM);

        BigDBUser restrictedUser =
                new BigDBUser("test", "test",
                        Collections.<BigDBGroup>emptyList());
        AuthContext authContext = makeAuthContext(Method.GET,
                queryPathString, restrictedUser);

        LocationPathExpression hookPath =
                LocationPathExpression.parse(hookPathString);
        AuthorizationHook.Result mockRejectionResult =
                new AuthorizationHook.Result(AuthorizationHook.Decision.REJECT,
                        AUTHORIZATION_HOOK_REJECT_MESSAGE);
        MockAuthorizationHook hook =
                new MockAuthorizationHook(mockRejectionResult);
        treespace.getHookRegistry().registerAuthorizationHook(hookPath,
                AuthorizationHook.Operation.QUERY,
                AuthorizationHook.Stage.AUTHORIZATION, authorizeList, hook);

        MockAuthorizationHook rootAcceptHook =
                new MockAuthorizationHook(AuthorizationHook.Result.ACCEPT);
        treespace.getHookRegistry().registerAuthorizationHook(
                LocationPathExpression.ROOT_PATH,
                AuthorizationHook.Operation.QUERY,
                AuthorizationHook.Stage.AUTHORIZATION, false, rootAcceptHook);

        Query query = Query.parse(queryPathString);

        String testInfoString = String.format("path: \"%s\"; query: \"%s\"",
                hookPathString, queryPathString);

        try {
            @SuppressWarnings("unused")
            DataNodeSet dataNodeSet = treespace.queryData(query, authContext);
            if (failureExpected)
                fail("Expected authorization failure; " + testInfoString);
            else
                assertThat(testInfoString, hook.getAuthorizeCallCounter(), is(0));
        }
        catch (AuthorizationException e) {
            if (failureExpected) {
                assertThat(testInfoString, hook.getAuthorizeCallCounter(), is(1));
                assertThat(e.getMessage(), containsString(AUTHORIZATION_HOOK_REJECT_MESSAGE));
            } else {
                fail("Expected authorization success; " + testInfoString);
            }
        }

        treespace.getHookRegistry().unregisterAuthorizationHook(hookPath,
                AuthorizationHook.Operation.QUERY,
                AuthorizationHook.Stage.AUTHORIZATION, false, hook);
    }

    @Test
    public void queryAuthHook1() throws Exception {
        doQueryAuthHookTest("/hook-test", "/hook-test/child1", false, true);
    }

    @Test
    public void queryAuthHook2() throws Exception {
        doQueryAuthHookTest("/hook-test/child1", "/hook-test/child1", false, true);
    }

    @Test
    public void queryAuthHook3() throws Exception {
        doQueryAuthHookTest("/hook-test/child4/child4.1", "/hook-test/child4", false, true);
    }

    @Test
    public void queryAuthHook4() throws Exception {
        doQueryAuthHookTest("/hook-test/child4/child4.1", "/hook-test/child4/child4.2", false, false);
    }

    @Test
    @Ignore("This test depends on the treespace support for authorizing " +
            "empty query results which is currently disabled because it " +
            "didn't work in all cases")
    public void queryAuthHook5() throws Exception {
        doQueryAuthHookTest("/hook-test/child5", "/hook-test/child5", true, true);
    }

    private static final String VALIDATION_HOOK_1_MESSAGE = "child1 too big";

    @Test
    public void validationHook1() throws Exception {
        final String leafPath = "/hook-test/child1";
        Query query = Query.parse(leafPath);
        ValidationHook validationHook = new ValidationHook() {
            @Override
            public Result validate(Context context) throws BigDBException {
                DataNode hookDataNode = context.getHookDataNode();
                return (hookDataNode.getLong() > 1000)
                        ? new Result(Decision.INVALID, VALIDATION_HOOK_1_MESSAGE)
                        : Result.VALID;
            }
        };
        treespace.getHookRegistry().registerValidationHook(query.getBasePath(),
                false, validationHook);
        AuthContext authContext =
                makeAuthContext(Method.GET, leafPath,
                        BigDBAuthTestUtils.ADMIN_USER);
        replaceData(query, "1000", authContext);
        try {
            replaceData(query, "1001", authContext);
            fail("Expected validation failure");
        } catch (ValidationException e) {
            assertThat(e.getMessage(), containsString(VALIDATION_HOOK_1_MESSAGE));
        }
    }

    @Test
    public void simpleFilterHook() throws Exception {
        final String listPath = "/hook-test/child5";
        Query query = Query.parse(listPath);
        FilterHook filterHook = new FilterHook() {
            @Override
            public Result filter(Context context) {
                DataNode hookDataNode = context.getHookDataNode();
                AuthContext authContext = context.getAuthContext();
                BigDBUser user = authContext.getUser();
                if (user.getUser().equals("test")) {
                    try {
                        DataNode child = hookDataNode.getChild("child5.2");
                        if (child.getLong() > 100)
                            return FilterHook.Result.EXCLUDE;
                    }
                    catch (BigDBException e) {
                        fail("Unexpected exception in filter hook");
                    }
                }
                return FilterHook.Result.INCLUDE;
            }
        };
        AuthContext adminAuthContext = makeAuthContext(Method.GET, listPath, BigDBAuthTestUtils.ADMIN_USER);
        replaceData(query, "[{\"child5.1\": \"foo\", \"child5.2\": 100}, {\"child5.1\": \"bar\", \"child5.2\": 200}]", adminAuthContext);
        treespace.getHookRegistry().registerFilterHook(query.getBasePath(), filterHook);
        MockAuthorizationHook rootAcceptHook =
                new MockAuthorizationHook(AuthorizationHook.Result.ACCEPT);
        treespace.getHookRegistry().registerAuthorizationHook(
                LocationPathExpression.ROOT_PATH,
                AuthorizationHook.Operation.QUERY,
                AuthorizationHook.Stage.AUTHORIZATION, false, rootAcceptHook);
        AuthContext testAuthContext = makeAuthContext(Method.GET, listPath, TEST_USER);
        DataNodeSet dataNodeSet = treespace.queryData(query, adminAuthContext);
        checkExpectedResult(dataNodeSet, "HookTest.SimpleFilterHook.1");
        dataNodeSet = treespace.queryData(query, testAuthContext);
        checkExpectedResult(dataNodeSet, "HookTest.SimpleFilterHook.2");
    }

    @Test
    public void filterNestedList() throws Exception {
        final String listPath = "/hook-test/child5";
        Query listQuery = Query.parse(listPath);
        LocationPathExpression nestedListPath = LocationPathExpression.parse("/hook-test/child5/child5.3");

        AuthContext adminAuthContext = makeAuthContext(Method.GET, listPath, BigDBAuthTestUtils.ADMIN_USER);
        AuthContext testAuthContext = makeAuthContext(Method.GET, listPath, TEST_USER);

        FilterHook filterHook = new FilterHook() {
            @Override
            public Result filter(Context context) {
                AuthContext authContext = context.getAuthContext();
                BigDBUser user = authContext.getUser();
                if (user.getUser().equals("test")) {
                    try {
                        LocationPathExpression hookPath = context.getHookPath();
                        Step listStep = hookPath.getSteps().get(1);
                        String listKey = listStep.getExactMatchPredicateString("child5.1");
                        if ((listKey != null) && listKey.equals("foo"))
                            return FilterHook.Result.EXCLUDE;
                    }
                    catch (BigDBException e) {
                        fail("Unexpected exception in filter hook");
                    }
                }
                return FilterHook.Result.INCLUDE;
            }
        };
        replaceData(listQuery, "[" +
                "{\"child5.1\": \"foo\", \"child5.2\": 100, \"child5.3\": [{\"child5.3.1\": 1000}]}," +
                "{\"child5.1\": \"bar\", \"child5.2\": 200, \"child5.3\": [{\"child5.3.1\": 1001}]}" +
                "]", adminAuthContext);
        treespace.getHookRegistry().registerFilterHook(nestedListPath, filterHook);
        MockAuthorizationHook rootAcceptHook =
                new MockAuthorizationHook(AuthorizationHook.Result.ACCEPT);
        treespace.getHookRegistry().registerAuthorizationHook(
                LocationPathExpression.ROOT_PATH,
                AuthorizationHook.Operation.QUERY,
                AuthorizationHook.Stage.AUTHORIZATION, false, rootAcceptHook);
        Query query1 = Query.parse("/hook-test/child5[child5.1=\"foo\"]/child5.3");
        DataNodeSet dataNodeSet = treespace.queryData(query1, testAuthContext);
        checkExpectedResult(dataNodeSet, "HookTest.FilterNestedList.1");
        Query query2 = Query.parse("/hook-test/child5[child5.1=\"bar\"]/child5.3");
        dataNodeSet = treespace.queryData(query2, testAuthContext);
        checkExpectedResult(dataNodeSet, "HookTest.FilterNestedList.2");
    }

    @Test
    public void leafFilterHook() throws Exception {
        final String listPath = "/hook-test/child5";
        Query listQuery = Query.parse(listPath);
        final String leafPath = "/hook-test/child5/child5.2";
        Query leafQuery = Query.parse(leafPath);

        FilterHook filterHook = new FilterHook() {
            @Override
            public Result filter(Context context) {
                AuthContext authContext = context.getAuthContext();
                BigDBUser user = authContext.getUser();
                if (user.getUser().equals("test")) {
                    return FilterHook.Result.EXCLUDE;
                }
                return FilterHook.Result.INCLUDE;
            }
        };
        treespace.getHookRegistry().registerFilterHook(leafQuery.getBasePath(), filterHook);
        MockAuthorizationHook rootAcceptHook =
                new MockAuthorizationHook(AuthorizationHook.Result.ACCEPT);
        treespace.getHookRegistry().registerAuthorizationHook(
                LocationPathExpression.ROOT_PATH,
                AuthorizationHook.Operation.QUERY,
                AuthorizationHook.Stage.AUTHORIZATION, false, rootAcceptHook);

        AuthContext adminAuthContext = makeAuthContext(Method.GET, listPath, BigDBAuthTestUtils.ADMIN_USER);
        replaceData(listQuery, "[{\"child5.1\": \"foo\", \"child5.2\": 100}, {\"child5.1\": \"bar\", \"child5.2\": 200}]", adminAuthContext);
        AuthContext testAuthContext = makeAuthContext(Method.GET, listPath, TEST_USER);
        DataNodeSet dataNodeSet = treespace.queryData(listQuery, adminAuthContext);
        checkExpectedResult(dataNodeSet, "HookTest.LeafFilterHook.1");
        dataNodeSet = treespace.queryData(listQuery, testAuthContext);
        checkExpectedResult(dataNodeSet, "HookTest.LeafFilterHook.2");
    }

    class TestValidationHook implements ValidationHook {

        private Result result;
        private List<LocationPathExpression> expectedPaths;
        private int callCount = 0;

        public TestValidationHook(Result result,
                List<LocationPathExpression> expectedPaths) {
            this.result = result;
            this.expectedPaths = expectedPaths;
        }

        @Override
        public Result validate(Context context) {
            LocationPathExpression hookPath = context.getHookPath();
            assertTrue(callCount < expectedPaths.size());
            assertThat(hookPath, is(expectedPaths.get(callCount)));
            callCount++;
            return result;
        }

        public int getCallCount() {
            return callCount;
        }

        public void resetCallCount() {
            callCount = 0;
        }
    }

    @Test
    public void deleteLastListElement() throws Exception {
        final String VALIDATION_ERROR_MESSAGE = "List cannot be empty";

        String initData = "{\"hook-test\":{\"child5\":[{\"child5.1\":\"foo\"}]}}";
        replaceData(Query.ROOT_QUERY, initData, AuthContext.SYSTEM);

        final LocationPathExpression listPath =
                LocationPathExpression.parse("/hook-test/child5");
        ValidationHook validationHook = new ValidationHook() {
            @Override
            public Result validate(Context context) throws BigDBException {
                LocationPathExpression hookPath = context.getHookPath();
                assertThat(hookPath, is(listPath));
                boolean isListValidation = context.isListValidation();
                assertThat(isListValidation, is(true));
                DataNode hookDataNode = context.getHookDataNode();
                assertThat(hookDataNode.childCount(), is(0));
                return new Result(Decision.INVALID, VALIDATION_ERROR_MESSAGE);
            }
        };
        treespace.getHookRegistry().registerValidationHook(listPath, true, validationHook);
        try {
            Query deleteQuery = Query.parse("/hook-test/child5[child5.1=\"foo\"]");
            treespace.deleteData(deleteQuery, AuthContext.SYSTEM);
            fail("Expected validation error");
        }
        catch (ValidationException e) {
            assertThat(e.getMessage().contains(VALIDATION_ERROR_MESSAGE), is(true));
        }
    }

    class TestWatchHook implements WatchHook {

        List<LocationPathExpression> expectedPaths;
        private int callCount = 0;

        public TestWatchHook(List<LocationPathExpression> expectedPaths) {
            this.expectedPaths = expectedPaths;
        }

        @Override
        public void watch(Context context) {
            LocationPathExpression hookPath = context.getHookPath();
            assertTrue(callCount < expectedPaths.size());
            assertThat(hookPath, is(expectedPaths.get(callCount)));
            callCount++;
        }

        public int getCallCount() {
            return callCount;
        }

        public void resetCallCount() {
            callCount = 0;
        }
    }

    @Test
    public void leafWatchHook() throws Exception {
        final String leafPathString = "/hook-test/child1";
        final LocationPathExpression leafPath =
                LocationPathExpression.parse(leafPathString);
        final Query leafQuery = Query.parse(leafPathString);
        TestWatchHook watchHook = new TestWatchHook(
                Collections.<LocationPathExpression>singletonList(leafPath));
        treespace.getHookRegistry().registerWatchHook(leafPath, false, watchHook);
        replaceData(leafQuery, "500", AuthContext.SYSTEM);
        assertThat(watchHook.getCallCount(), is(1));
        watchHook.resetCallCount();
        treespace.deleteData(leafQuery, AuthContext.SYSTEM);
        assertThat(watchHook.getCallCount(), is(1));
    }

    @Test
    public void containerWatchHook() throws Exception {
        final String containerPathString = "/hook-test/child4";
        final LocationPathExpression containerPath =
                LocationPathExpression.parse(containerPathString);
        final Query containerQuery = Query.parse(containerPathString);
        TestWatchHook watchHook = new TestWatchHook(
                Collections.<LocationPathExpression>singletonList(containerPath));
        treespace.getHookRegistry().registerWatchHook(containerPath, false, watchHook);
        replaceData(containerQuery, "{\"child4.1\":\"foo\",\"child4.2\":100}",
                AuthContext.SYSTEM);
        assertThat(watchHook.callCount, is(1));
        watchHook.resetCallCount();
        treespace.deleteData(containerQuery, AuthContext.SYSTEM);
        assertThat(watchHook.getCallCount(), is(1));
    }

    @Test
    public void listWatchHook() throws Exception {
        final String listPathString = "/hook-test/child5";
        final LocationPathExpression listPath =
                LocationPathExpression.parse(listPathString);
        final String listElementPathString1 =
                "/hook-test/child5[child5.1=\"bar\"]";
        final LocationPathExpression listElementPath1 =
                LocationPathExpression.parse(listElementPathString1);
        final Query listElementQuery1 = Query.parse(listElementPathString1);
        final LocationPathExpression listElementPath2 =
                LocationPathExpression.parse("/hook-test/child5[child5.1=\"foo\"]");
        final Query listQuery = Query.parse(listPathString);
        TestWatchHook listWatchHook = new TestWatchHook(
                Collections.<LocationPathExpression>singletonList(listPath));
        treespace.getHookRegistry().registerWatchHook(listPath, true, listWatchHook);
        TestWatchHook listElementWatchHook = new TestWatchHook(
                ImmutableList.of(listElementPath1, listElementPath2));
        treespace.getHookRegistry().registerWatchHook(listPath, false, listElementWatchHook);

        // Initialize the list with two elements. Verify that the list-level
        // watch hook only gets called once and with the right hook path.
        replaceData(listQuery, "[{\"child5.1\":\"bar\", \"child5.2\": 123}," +
                "{\"child5.1\":\"foo\", \"child5.2\": 456}]",
                AuthContext.SYSTEM);
        assertThat(listWatchHook.getCallCount(), is(1));
        assertThat(listElementWatchHook.getCallCount(), is(2));

        listWatchHook.resetCallCount();
        listElementWatchHook.resetCallCount();
        replaceData(listElementQuery1, "{\"child5.1\":\"bar\", \"child5.2\": 345}",
                AuthContext.SYSTEM);
        assertThat(listWatchHook.getCallCount(), is(1));
        assertThat(listElementWatchHook.getCallCount(), is(1));

        listWatchHook.resetCallCount();
        listElementWatchHook.resetCallCount();
        treespace.deleteData(listQuery, AuthContext.SYSTEM);
        assertThat(listWatchHook.getCallCount(), is(1));
        assertThat(listElementWatchHook.getCallCount(), is(2));
    }

    // FIXME: Need more thorough unit tests!!!
}
