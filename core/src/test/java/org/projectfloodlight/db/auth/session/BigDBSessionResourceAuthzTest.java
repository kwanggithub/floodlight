package org.projectfloodlight.db.auth.session;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.auth.BigDBAuthTestUtils;
import org.projectfloodlight.db.auth.session.BigDBSessionResource;
import org.projectfloodlight.db.auth.session.Session;
import org.projectfloodlight.db.auth.session.SessionManager;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.db.service.internal.TreespaceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

import com.google.common.collect.Iterables;

public class BigDBSessionResourceAuthzTest extends BigDBAuthTestUtils {
    protected static Logger logger =
        LoggerFactory.getLogger(BigDBSessionResourceAuthzTest.class);

    private SessionManager sessionManager;
    private Session adminSession;
    private Session nonAdminSession;

    @Override
    @Before
    public void setUp() throws Exception {
        enableTrace(BigDBSessionResource.class);
        enableTrace(TreespaceImpl.class);

        super.setUp();
        createUser(ADMIN_USER, "admin");
        createUser(NON_ADMIN_USER, "admin");

        sessionManager = bigDB.getService().getAuthService().getSessionManager();
        adminSession = sessionManager.createSession(ADMIN_USER);
        nonAdminSession = sessionManager.createSession(NON_ADMIN_USER);
    }

    private void enableTrace(Class<?> class1) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(class1);
        logger.setLevel(Level.TRACE);
    }

    @Test
    public void testQueryAllAdmin() throws Exception {
        Treespace treespace = bigDB.getControllerTreespace();
        DataNodeSet result = treespace.queryData(Query.parse("/core/aaa/session"), AuthContext.forSession(adminSession, MOCK_REQUEST));
        assertThat(Iterables.size(result), is(2));
        assertThat(result, containsSession(adminSession));
        assertThat(result, containsSession(nonAdminSession));
    }

    @Test
    public void testQueryNonAllAdmin() throws Exception {
        Treespace treespace = bigDB.getControllerTreespace();
        DataNodeSet result = treespace.queryData(Query.parse("/core/aaa/session"), AuthContext.forSession(nonAdminSession, MOCK_REQUEST));
        assertThat(Iterables.size(result), is(1));
        assertThat(result, not(containsSession(adminSession)));
        assertThat(result, containsSession(nonAdminSession));
    }

    @Test
    public void testQuerySpecNonAdmin() throws Exception {
        Treespace treespace = bigDB.getControllerTreespace();

        DataNodeSet result = treespace.queryData(Query.parse("/core/aaa/session[id="+nonAdminSession.getId()+"]"), AuthContext.forSession(nonAdminSession, MOCK_REQUEST));
        assertThat(Iterables.size(result), is(1));
        assertThat(result, not(containsSession(adminSession)));
        assertThat(result, containsSession(nonAdminSession));

        result = treespace.queryData(Query.parse("/core/aaa/session[auth-token='"+nonAdminSession.getCookie()+"']"), AuthContext.forSession(nonAdminSession, MOCK_REQUEST));
        assertThat(Iterables.size(result), is(1));
        assertThat(result, not(containsSession(adminSession)));
        assertThat(result, containsSession(nonAdminSession));

        result = treespace.queryData(Query.parse("/core/aaa/session[id="+adminSession.getId()+"]"), AuthContext.forSession(nonAdminSession, MOCK_REQUEST));
        // shouldn't find any admin session
        assertThat(Iterables.size(result), is(0));

        result = treespace.queryData(Query.parse("/core/aaa/session[auth-token='"+adminSession.getCookie()+"']"), AuthContext.forSession(nonAdminSession, MOCK_REQUEST));
        assertThat(Iterables.size(result), is(0));
    }

    @Test
    public void testQuerySpecAdmin() throws Exception {
        Treespace treespace = bigDB.getControllerTreespace();

        DataNodeSet result;
        result = treespace.queryData(Query.parse("/core/aaa/session[id="+nonAdminSession.getId()+"]"), AuthContext.forSession(adminSession, MOCK_REQUEST));
        assertThat(Iterables.size(result), is(1));
        assertThat(result, not(containsSession(adminSession)));
        assertThat(result, containsSession(nonAdminSession));

        // even admin doesn't see other sesssions by auth-token
        result = treespace.queryData(Query.parse("/core/aaa/session[auth-token='"+nonAdminSession.getCookie()+"']"), AuthContext.forSession(adminSession, MOCK_REQUEST));
        assertThat(Iterables.size(result), is(0));

        result = treespace.queryData(Query.parse("/core/aaa/session[id="+adminSession.getId()+"]"), AuthContext.forSession(adminSession, MOCK_REQUEST));
        assertThat(result, containsSession(adminSession));
        assertThat(result, not(containsSession(nonAdminSession)));

        result = treespace.queryData(Query.parse("/core/aaa/session[auth-token='"+adminSession.getCookie()+"']"), AuthContext.forSession(adminSession, MOCK_REQUEST));
        assertThat(result, containsSession(adminSession));
        assertThat(result, not(containsSession(nonAdminSession)));
    }

    @Test
    public void testDeleteAdminByIdOwn() throws Exception {
        Treespace treespace = bigDB.getControllerTreespace();
        treespace.deleteData(Query.parse("/core/aaa/session[id="+adminSession.getId()+"]"), AuthContext.forSession(adminSession, MOCK_REQUEST));
        assertThat(Iterables.size(sessionManager.getActiveSessions()), is(1));
        assertThat(sessionManager.getActiveSessions().iterator().next(), is(nonAdminSession));
    }

    @Test
    public void testDeleteNonAdminByAdmin() throws Exception {
        Treespace treespace = bigDB.getControllerTreespace();
        treespace.deleteData(Query.parse("/core/aaa/session[id="+nonAdminSession.getId()+"]"), AuthContext.forSession(adminSession, MOCK_REQUEST));
        assertThat(Iterables.size(sessionManager.getActiveSessions()), is(1));
        assertThat(sessionManager.getActiveSessions().iterator().next(), is(adminSession));
    }

    @Test
    public void testDeleteNonAdminByIdOwn() throws Exception {
        Treespace treespace = bigDB.getControllerTreespace();
        treespace.deleteData(Query.parse("/core/aaa/session[id="+nonAdminSession.getId()+"]"), AuthContext.forSession(nonAdminSession, MOCK_REQUEST));
        assertThat(Iterables.size(sessionManager.getActiveSessions()), is(1));
        assertThat(sessionManager.getActiveSessions().iterator().next(), is(adminSession));
    }

    @Test
    public void testDeleteAdminByNonAdmin() throws Exception {
        Treespace treespace = bigDB.getControllerTreespace();
        treespace.deleteData(Query.parse("/core/aaa/session[id="+adminSession.getId()+"]"), AuthContext.forSession(nonAdminSession, MOCK_REQUEST));
        // Non-admin can't see the admin session so the admin session is filtered out so nothing is deleted.
        assertThat(Iterables.size(sessionManager.getActiveSessions()), is(2));
    }

    @Test
    public void testDeleteAdminAll() throws Exception {
        Treespace treespace = bigDB.getControllerTreespace();
        treespace.deleteData(Query.parse("/core/aaa/session"), AuthContext.forSession(adminSession, MOCK_REQUEST));
        assertThat(Iterables.size(sessionManager.getActiveSessions()), is(0));
    }

    @Test
    public void testDeleteNonAdminAll() throws Exception {
        Treespace treespace = bigDB.getControllerTreespace();
        treespace.deleteData(Query.parse("/core/aaa/session"), AuthContext.forSession(nonAdminSession, MOCK_REQUEST));
        assertThat(Iterables.size(sessionManager.getActiveSessions()), is(1));
        assertThat(sessionManager.getActiveSessions().iterator().next(), is(adminSession));
    }

    private Matcher<DataNodeSet> containsSession(final Session adminSession2) {
        return new BaseMatcher<DataNodeSet>() {
            @Override
            public boolean matches(Object item) {
                if(!(item instanceof DataNodeSet))
                    return false;
                DataNodeSet set = (DataNodeSet) item;
                for(DataNode n : set) {
                    try {
                        if(n.getChild("id").getLong(-1) == adminSession2.getId()) {
                            return true;
                        }
                    } catch (BigDBException e) {
                        throw new RuntimeException(e);
                    }
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("should contain session with id "+adminSession2.getId());
            }
        };
    }



}
