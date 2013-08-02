package net.bigdb.rest.auth;

import java.util.concurrent.ConcurrentMap;

import net.bigdb.BigDBException;
import net.bigdb.auth.AuditEvent;
import net.bigdb.auth.AuthService;
import net.bigdb.auth.AuthenticationException;
import net.bigdb.auth.AuthenticationResult;
import net.bigdb.auth.session.Session;
import net.bigdb.rest.BigDBResource;
import net.bigdb.rest.BigDBRestApplication;

import org.restlet.data.CookieSetting;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Restlet Resource handling the AAA login protocol. Note that his is part
 *  of the access mechanism, not BigDB as such.
 *
 *  @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class LoginResource extends BigDBResource {
    public final static String COOKIE_NAME = "session_cookie";

    protected final static Logger logger = LoggerFactory.getLogger(LoginResource.class);

    private AuthService authService;

    @Override
    protected void doInit() throws ResourceException {
        super.doInit();

        ConcurrentMap<String, Object> attributes = getContext().getAttributes();
        this.authService = (AuthService) attributes.get(BigDBRestApplication.BIGDB_AUTH_SERVICE);
        if (this.authService == null)
            throw new IllegalArgumentException(
                    "Must provide an authService in restlet context attribute"+BigDBRestApplication.BIGDB_AUTH_SERVICE);
    }

    @Post
    public LoginResult login(LoginRequest loginInfo) throws BigDBException {
        try {
            loginInfo.validate();

            AuthenticationResult result =
                    authService.getAuthenticator().authenticate(loginInfo, getRequest().getClientInfo());

            if (!result.isSuccess()) {
                getResponse().setStatus(result.getStatus());
                logger.warn("Authentication failed: " + result.getInternalReason());
                return LoginResult.failure(result.getExternalReason());
            }

            Session session = authService.getSessionManager().createSession(result.getUser());
            session.setLastAddress(ProxyUtils.getUpstreamAddress(authService.getConfig(), getRequest()));

            getResponse().getCookieSettings().add(
                    new CookieSetting(COOKIE_NAME, session.getCookie()));

            // Audit login success
            AuditEvent event = session.eventBuilder()
                .type(Session.CREATE_TYPE)
                .build();
            event.commit();
            return LoginResult.success(session.getCookie());

        } catch (BadLoginException e) {
            // do not usually log stack trace here, BadLoginException
            // does not indicate a program error
            logger.info("Bad Login Request: " + e.getMessage());
            if(logger.isDebugEnabled()) {
                logger.debug("   BadLoginException stacktrace: ", e);
            }

            getResponse().setStatus(e.getStatus());
            return LoginResult.failure(e.getMessage());
        } catch (AuthenticationException e) {
            logger.warn("Error on authentication: " + e.getInternalMessage(), e);
            getResponse().setStatus(e.getStatus());
            return LoginResult.failure(e.getMessage());
        }
    }

    static class LoginResult {
        public final boolean success;
        public final String error_message;
        public final String session_cookie;

        public LoginResult(boolean success, String sessionCookie, String errorMessage) {
            this.success = success;
            this.session_cookie = sessionCookie;
            this.error_message = errorMessage;
        }

        public static LoginResult success(String sessionId) {
            return new LoginResult(true, sessionId, "");
        }

        public static LoginResult failure(String message) {
            return new LoginResult(false, null, message);
        }

    }

}
