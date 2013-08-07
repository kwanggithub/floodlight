package org.projectfloodlight.db.rest.auth;

import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.auth.AuthorizationException;
import org.projectfloodlight.db.auth.application.ApplicationContext;
import org.projectfloodlight.db.auth.application.ApplicationRegistry;
import org.projectfloodlight.db.auth.session.Session;
import org.projectfloodlight.db.auth.session.SessionManager;
import org.restlet.Request;
import org.restlet.data.Cookie;
import org.restlet.engine.header.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthContextFactory {
    private final SessionManager sessionManager;
    private final ApplicationRegistry applications;

    private final AuthConfig authConfig;

    protected final static Logger logger = LoggerFactory.getLogger(AuthContextFactory.class);

    public AuthContextFactory(SessionManager sessionManager, ApplicationRegistry applications, AuthConfig authConfig) {
        this.sessionManager = sessionManager;
        this.applications = applications;
        this.authConfig = authConfig;
    }

    public AuthContext getAuthContext(Request request) throws AuthorizationException {

        Cookie cookie = request.getCookies().getFirst(LoginResource.COOKIE_NAME);
        Session session = null;
        String msg;

        if (cookie != null) {
            session = sessionManager.getSessionForCookie(cookie.getValue());

            if (session != null) {
                session.setLastAddress(ProxyUtils.getUpstreamAddress(authConfig, request));
                return AuthContext.forSession(session, request);
            }

            msg = "No session found for cookie";
        } else {
            msg = "No session cookie provided";
        }

        @SuppressWarnings("unchecked")
        Series<Header> headers = (Series<Header>) request.getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
        String appAuth = headers.getFirstValue("Authorization");

        if (appAuth != null) {
            logger.debug("app auth key is {}", appAuth);
            ApplicationContext app = applications.getApplication(request);
            if (app != null)
                return AuthContext.forApplication(app, request);
            msg = "application key is invalid";
        }

        if (authConfig.getParam(AuthConfig.ENABLE_NULL_AUTHENTICATION)) {
            return null;
        } else {
            throw new AuthenticationRequiredException(msg);
        }

    }


}
