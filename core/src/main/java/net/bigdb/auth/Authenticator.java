package net.bigdb.auth;

import net.bigdb.rest.auth.LoginRequest;

import org.restlet.data.ClientInfo;


/**
 * Interface contract for a module that <b>authenticates</b> users in BigDB.
 * <p>
 * <b>NOTE:</b>Implementations are required to be <b>thread safe</b>.
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 */
public interface Authenticator {

    /**
     * attempt to authenticate the given user
     *
     * @param request
     *            LoginRequest object encapsulating the request parameters
     * @param clientInfo
     *            the Restlet clientInfo, e.g., for finding out the client's IP
     *            address, user agent.
     * @return an AuthenticationResult conveying the result of the
     *         authentication
     * @throws AuthenticationException
     *             if exceptional condition occurs (e.g., auth server
     *             unavailable)
     */
    public AuthenticationResult authenticate(LoginRequest request, ClientInfo clientInfo)
            throws AuthenticationException;
}
