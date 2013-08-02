package net.bigdb.auth.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.auth.AuditEvent;
import net.bigdb.auth.AuthContext;
import net.bigdb.auth.BigDBGroup;
import net.bigdb.auth.BigDBUser;
import net.bigdb.data.annotation.BigDBDelete;
import net.bigdb.data.annotation.BigDBParam;
import net.bigdb.data.annotation.BigDBProperty;
import net.bigdb.data.annotation.BigDBQuery;
import net.bigdb.data.annotation.BigDBSerialize;
import net.bigdb.data.serializers.ISOLongDateDataNodeSerializer;
import net.bigdb.hook.FilterHook;
import net.bigdb.query.Query;
import net.bigdb.query.Step;
import net.floodlightcontroller.bigdb.FloodlightResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.base.Strings;

/** Dynamic BigDBResource providing information and management of auth sessions
 *  ('w' logged in functionality)
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class BigDBSessionResource extends FloodlightResource {

    /** Visibility Filter Hook for Sessions: Allow access for admin globally */
    public static class SessionFilterHook implements FilterHook {
        @Override
        public Result filter(Context context) {
            AuthContext authContext = context.getAuthContext();
            if(authContext == null)
                return Result.INCLUDE;

            BigDBUser user = authContext.getUser();
            if(user.isAdmin()) {

                if(logger.isTraceEnabled()) {
                    logger.trace("SessionFilterHook: allowing "+ context + " (user is admin)");
                }

                return Result.INCLUDE;
            } else {
                String sessionOwnerName;
                try {
                    sessionOwnerName = context.getHookDataNode().getChild("user-info").getChild("user-name").getString();
                    if(Objects.equal(user.getUser(), sessionOwnerName)) {
                        if(logger.isTraceEnabled()) {
                            logger.trace("SessionFilterHook: allowing "+ context + " (session belongs to user)");
                        }
                        return Result.INCLUDE;
                    } else {
                        if(logger.isTraceEnabled()) {
                            logger.trace("SessionFilterHook: allowing "+ context + " (session does not belong to user)");
                        }
                        return Result.EXCLUDE;
                    }
                } catch (BigDBException e) {
                    logger.warn("Error querying user information from session: " + e.getMessage(), e);
                    return Result.EXCLUDE;
                }
            }
        }
    }

    public static class SessionUserInfo {
        private final String userName;
        private final String fullName;
        private final List<String> group;

        public SessionUserInfo(String userName, String fullName, List<String> groups) {
            this.userName = userName;
            this.fullName = fullName;
            this.group = groups;
        }

        public SessionUserInfo(BigDBUser user) {
            this.userName = user.getUser();
            this.fullName = user.getFullName();
            List<String> groups = new ArrayList<String>();
            for(BigDBGroup g: user.getGroups()) {
               groups.add(g.getName());
            }
            this.group = groups;
        }

        @BigDBProperty(value="user-name")
        public String getUserName() {
            return userName;
        }

        @BigDBProperty(value="full-name")
        public String getFullName() {
            return fullName;
        }

        @BigDBProperty(value="group")
        public List<String> getGroup() {
            return group;
        }
    }

    public static class SessionInfo {
        private final long id;
        private final SessionUserInfo userInfo;
        private final String lastAddress;
        private final long created;
        private final long lastTouched;
        private final String authToken;

        public SessionInfo(Session session, boolean includeAuthToken) {
            this.id = session.getId();
            created = session.getCreated();
            lastAddress = session.getLastAddress();
            lastTouched = session.getLastTouched();
            userInfo = new SessionUserInfo(session.getUser());
            authToken = includeAuthToken ? session.getCookie() : null;
        }

        @BigDBProperty(value="id")
        public long getId() {
            return id;
        }

        @BigDBProperty(value="user-info")
        public SessionUserInfo getUserInfo() {
            return userInfo;
        }

        @BigDBProperty(value="last-address")
        public String getLastAddress() {
            return lastAddress;
        }

        @BigDBSerialize(using=ISOLongDateDataNodeSerializer.class)
        public long getCreated() {
            return created;
        }

        @BigDBProperty(value="last-touched")
        @BigDBSerialize(using=ISOLongDateDataNodeSerializer.class)
        public long getLastTouched() {
            return lastTouched;
        }

        @BigDBProperty(value="auth-token")
        public String getAuthToken() {
            return authToken;
        }
    }

    private final static Logger logger =
            LoggerFactory.getLogger(BigDBSessionResource.class);
    private final SessionManager sessionManager;

    public BigDBSessionResource(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    private boolean isSessionSelected(Query query) {
        Step sessionStep = query.getSteps().get(0);
        return sessionStep.getExactMatchPredicateValue("id") != null ||
               !Strings.isNullOrEmpty((String) sessionStep.getExactMatchPredicateValue("auth-token"));
    }

    private Session getSelectedSession(Query query) {
        Step sessionStep = query.getSteps().get(0);
        Object idObj = sessionStep.getExactMatchPredicateValue("id");
        if (idObj != null) {
            long sessionId;
            if (idObj instanceof Number)
                sessionId = ((Number) idObj).longValue();
            else
                sessionId = Long.valueOf(idObj.toString());
            return sessionManager.getSessionForId(sessionId);
        }

        String sessionCookie = (String) sessionStep.getExactMatchPredicateValue("auth-token");
        if(sessionCookie != null)
            return sessionManager.getSessionForCookie(sessionCookie);

        return null;
    }

    @BigDBDelete
    public void deleteSession(@BigDBParam("query") Query query) throws BigDBException {

        if(!isSessionSelected(query))
            throw new IllegalArgumentException("Session must be selected by either session id or auth token");
        Session session = getSelectedSession(query);

        if(session != null) {
            AuditEvent event = session.eventBuilder()
                .type(Session.REMOVE_TYPE)
                .build();
            event.commit();
            sessionManager.removeSession(session);
        }
    }

    @BigDBQuery
    public Iterator<SessionInfo> getSessions(@BigDBParam("query") Query query,
            @BigDBParam("auth-context") AuthContext authContext)
                    throws BigDBException {
        Collection<SessionInfo> results;
        Session currentSession = (authContext != null) ? authContext.getSessionData() : null;
        if(isSessionSelected(query)) {
            Session session = getSelectedSession(query);
            //Step sessionStep = query.getSteps().get(0);
            //String providedAuthToken = (String) sessionStep.getExactMatchPredicateValue("auth-token");
            results = session != null ? Collections.singleton(new SessionInfo(session, (session == currentSession))) : Collections.<SessionInfo>emptySet();
        } else {
            results = new ArrayList<SessionInfo>();
            for(Session session: sessionManager.getActiveSessions()) {
                results.add(new SessionInfo(session, (session == currentSession)));
            }
        }

        if(logger.isDebugEnabled())
            logger.debug("getSessions: "+results);

        return results.iterator();
    }

}
