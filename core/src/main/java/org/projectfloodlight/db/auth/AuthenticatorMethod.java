package org.projectfloodlight.db.auth;

import java.util.Collections;
import java.util.Set;

import org.projectfloodlight.db.rest.auth.LoginRequest;
import org.restlet.data.ClientInfo;
import org.restlet.data.Status;

import com.google.common.collect.ImmutableSet;

/**
 * Interface for individual authentication providers
 *
 * Implementors of this interface should provide concrete versions of getPrincipal()
 * and/or getGroups().
 * In cases where getPrincipal() or getGroups() are not implemented by
 * the provider, return the 'notImplemented()' status objects.
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public interface AuthenticatorMethod {

    /** Represent the result of password authentication
     *
     * A successful authentication result includes a 'principal'
     * (canonical user id) and an optional list of initial groups,
     * if this authentication method also implements authorization.
     *
     * A method that <em>does</em> implement a separate getGroups()
     * step should disregard the initial group list.
     *
     * @author Carl Roth <carl.roth@bigswitch.com>
     *
     */
    public static class AuthnResult {
        private final String principal;
        public String getPrincipal() {
            return principal;
        }

        private final String fullName;
        public String getFullName() {
            return fullName;
        }

        private final boolean authoritative;
        public boolean isAuthoritative() {
            return authoritative;
        }

        private final boolean success;
        public boolean isSuccess() {
            return authoritative && success;
        }

        private final Status status;
        public Status getStatus() {
            return status;
        }

        private final Set<String> initialGroups;
        public Set<String> getInitialGroups() {
            return initialGroups;
        }

        private AuthnResult(String p, String f, Set<String> g,
                            Status sts, boolean s, boolean a) {
            this.principal = p;
            this.fullName = f;
            this.initialGroups = ImmutableSet.copyOf(g);
            this.status = sts;
            this.success = s;
            this.authoritative = a;
        }

        /** password is valid, here is your principal */
        public static AuthnResult success(String principal, String fullName) {
            return new AuthnResult(principal, fullName, Collections.<String>emptySet(),
                                   Status.SUCCESS_OK,
                                   true, true);
        }
        /** password is valid, here is your principal and group list */
        public static AuthnResult success(String principal, String fullName, Set<String> groups) {
            return new AuthnResult(principal, fullName, groups,
                                   Status.SUCCESS_OK,
                                   true, true);
        }
        /** this authn failed, continue with other methods */
        public static AuthnResult notAuthenticated() {
            return new AuthnResult(null, null, Collections.<String>emptySet(),
                                   Status.CLIENT_ERROR_UNAUTHORIZED,
                                   false, false);
        }
        /** this authn failed, do not attempt other methods */
        public static AuthnResult failure(Status status) {
            return new AuthnResult(null, null, Collections.<String>emptySet(), status, false, true);
        }
        /** authn was not verified, continue with other methods */
        public static AuthnResult notImplemented() {
            return new AuthnResult(null, null, Collections.<String>emptySet(),
                                   Status.SERVER_ERROR_SERVICE_UNAVAILABLE,
                                   false, false);
        }
        // XXX roth -- maybe also 'conditional', which is 'success' but not 'authoritative'...
    }

    /** result of authorization (group lookup for a validated principal)
     *
     * A successful authorization includes a group list to
     * use with platform authorization.
     *
     * @author Carl Roth <carl.roth@bigswitch.com>
     *
     */
    public static class AuthzResult {
        private final boolean authoritative;
        public boolean isAuthoritative() {
            return authoritative;
        }

        private final boolean success;
        public boolean isSuccess() {
            return authoritative && success;
        }

        private final Set<String> groups;
        public Set<String> getGroups() {
            return groups;
        }

        private final Status status;
        public Status getStatus() {
            return status;
        }

        private AuthzResult(Set<String> groups, Status sts,
                            boolean success, boolean authoritative) {
            this.groups = ImmutableSet.copyOf(groups);
            this.status = sts;
            this.success = success;
            this.authoritative = authoritative;
        }

        /** this principal is not authorized for access, try somewhere else */
        public static AuthzResult notAuthorized() {
            return new AuthzResult(Collections.<String>emptySet(),
                                   Status.CLIENT_ERROR_UNAUTHORIZED,
                                   false, false);
        }
        /** this principal is authorized with the following groups */
        public static AuthzResult success(Set<String> groups) {
            return new AuthzResult(groups, Status.SUCCESS_OK, true, true);
        }
        /** this method cannot determine if the principal is authorized */
        public static AuthzResult notImplemented() {
            return new AuthzResult(Collections.<String>emptySet(),
                                   Status.SERVER_ERROR_SERVICE_UNAVAILABLE,
                                   false, false);
        }
        /** this method failed to authorized the principal, do not continue */
        public static AuthzResult failure(Status sts) {
            return new AuthzResult(Collections.<String>emptySet(),
                                   sts,
                                   false, true);
        }
    }

    public static interface Registry {
        /** retrieve an unordered list of valid (registered) method names */
        public Iterable<String> getMethodNames() throws AuthenticationException;
        /** retrieve an ordered collection of methods for getPrincipal() */
        public Iterable<AuthenticatorMethod> getAuthnMethods() throws AuthenticationException;
        /** retrieve an ordered collection of methods for getGroups() */
        public Iterable<AuthenticatorMethod> getAuthzMethods() throws AuthenticationException;
    }

    /** validate a user password and get the login principal
     *
     * @param login user login name provided by the client
     * @param Password plaintext password provided by the client
     * @param service (optional) requested service
     * @param clientInfo Restlet ClientInfo
     * @return an AuthnResult, which includes a Restlet Status
     * as well as the canonical user principal that was validated
     * @throws AuthenticationException
     */
    public AuthnResult getPrincipal(LoginRequest request, ClientInfo clientInfo)
                                            throws AuthenticationException;

    /** get the groups to which this principal belongs
     *
     * @param principalContext result of password authentication,
     *   including principal and (optional) initial group list
     * @param service (optional) requested service description
     * @param clientInfo Restlet ClientInfo
     * @return an AuthzResult, which includes a Restlet Status,
     * along with a final list of groups for further authorization
     *
     * @throws AuthenticationException
     */
    public AuthzResult getGroups(AuthnResult principalContext,
                                 LoginRequest request, ClientInfo clientInfo)
                                         throws AuthenticationException;

}
