package net.bigdb.auth;

import net.bigdb.auth.application.ApplicationContext;
import net.bigdb.auth.session.Session;

import org.restlet.Request;

/**
 * context objects passed to Authorizers. Has information about the session the
 * Restlet request (if available), and the user.
 *
 * @see Authorizer
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class AuthContext {
    /**
     * static pseudo context used to indicate that a BigDB is issued by a
     * trusted system component and should not be subjected to authorization.
     */
    public static final AuthContext SYSTEM = new AuthContext(null, null, null);

    private final Session sessionData;
    private final ApplicationContext applicationData;

    // TODO: figure out if access to the restlet request is required / maybe
    // remove
    private final Request request;

    /* either sessionData or applicationData should be non-null to create a valid authContext;
     * otherwise the conneciton is un-authenticated.
     */
    private AuthContext(Session sessionData, ApplicationContext applicationData, Request request) {
        this.sessionData = sessionData;
        this.applicationData = applicationData;
        this.request = request;
    }

    public static AuthContext forSession(Session sessionData, Request request) {
        return new AuthContext(sessionData, null, request);
    }

    public static AuthContext forApplication(ApplicationContext applicationData, Request request) {
        return new AuthContext(null, applicationData, request);
    }

    public Session getSessionData() {
        return sessionData;
    }

    public ApplicationContext getApplicationData() {
        return applicationData;
    }

    public BigDBUser getUser() {
        return sessionData != null ? sessionData.getUser() : null;
    }

    public String getApplication() {
        return applicationData != null ? applicationData.getName() : null;
    }

    public Request getRequest() {
        return request;
    }

}
