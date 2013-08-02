package net.bigdb.auth;

import java.util.ArrayList;

import net.bigdb.auth.AuthenticatorMethod.AuthnResult;
import net.bigdb.auth.AuthenticatorMethod.AuthzResult;
import net.bigdb.rest.auth.LoginRequest;
import net.bigdb.service.Service;

import org.restlet.data.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** aggregate authentication source.
 *
 * Supports multiple authentication sources,
 * each one registered with it by a friendly name
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public class DynamicAuthenticator implements Authenticator {

    private final AuthenticatorMethod.Registry registry;
    private final LocalAuthenticator localAuthenticator;
    private final static Logger logger = 
            LoggerFactory.getLogger(DynamicAuthenticator.class);

    public DynamicAuthenticator(Service service,
                                LocalAuthenticator localAuthenticator,
                                AuthenticatorMethod.Registry registry) {
        this.registry = registry;
        this.localAuthenticator = localAuthenticator;
    }

    /** reset authentication source using a default password.
     *
     * Delegates the reset action to the "local" authenticator,
     * if it exists.
     *
     * @param cleartext password
     *
     */
    public void resetAdminPassword(String password) {
        localAuthenticator.resetAdminPassword(password);
    }

    /** validate the password against the authentication sources */
    private AuthnResult authn(LoginRequest request,
                              ClientInfo clientInfo)
                                      throws AuthenticationException {

        for (AuthenticatorMethod m : registry.getAuthnMethods()) {
            logger.debug("trying method {} for authn", m.getClass());
            AuthnResult ctx = m.getPrincipal(request, clientInfo);
            if (ctx.isAuthoritative()) {
                logger.debug("method {} (authn) is authoritative for {}", 
                             m.getClass(), request.getUser());
                return ctx;
            }
        }

        return AuthnResult.notAuthenticated();

    }

    /** validate the password against the authentication sources */
    private AuthzResult authz(AuthnResult context,
                              LoginRequest request,
                              ClientInfo clientInfo)
                                      throws AuthenticationException {

        for (AuthenticatorMethod m : registry.getAuthzMethods()) {
            logger.debug("trying method {} for authz", m.getClass());
            AuthzResult sts = m.getGroups(context, request, clientInfo);
            if (sts.isAuthoritative()) {
                logger.debug("method {} (authz) is authoritative for {}", 
                             m.getClass(), context.getPrincipal());
                return sts;
            }
        }

        return AuthzResult.notAuthorized();
    }

    /**
     * For each configured auth source, sorted by priority...
     * See if it is an actual authentication source,
     * then try to use it for authentication.
     * Otherwise (if there are no configured auth sources)
     * try to authenticate using the local auth source.
     */
    @Override
    public AuthenticationResult authenticate(LoginRequest request,
                                             ClientInfo clientInfo)
                                                throws AuthenticationException {

        AuthnResult r1 = authn(request, clientInfo);
        if (!r1.isSuccess()) {
            return AuthenticationResult.wrongPassword(request.getUser());
        }
        AuthzResult r2 = authz(r1, request, clientInfo);
        if (!r2.isSuccess()) {
            return AuthenticationResult.notAuthorized(r1.getPrincipal());
        }
        ArrayList<BigDBGroup> l = new ArrayList<BigDBGroup>();
        for (String s : r2.getGroups()) {
            l.add(new BigDBGroup(s));
        }
        BigDBUser u = new BigDBUser(r1.getPrincipal(), r1.getFullName(), l);
        return AuthenticationResult.success(u);
    }

}
