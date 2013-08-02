package net.bigdb.auth;

import net.bigdb.BigDBException;
import net.bigdb.auth.application.ApplicationRegistry;
import net.bigdb.auth.session.SessionManager;
import net.bigdb.hook.AuthorizationHook;
import net.bigdb.service.Service;

/*** abstract service interface for the Auth[nz] services in BigDB. Can be queried by Service.getAuthService()
 *
 * @see Service#getAuthService()
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public interface AuthService {
    /**
     * This provides a hook to do initialization before startUp.
     * @throws BigDBException
     */
    void init() throws BigDBException;

    void startUp() throws BigDBException;

    /** register a new authentication source.
     *
     * @param friendly name, ideally visible to the Cli
     * @param authn abstract authentication interface
     */
    void registerAuthenticator(String name, AuthenticatorMethod authn);

    /**
     * Register a new auditor for writing audit events to storage
     *
     * @param name Name of the auditor, for configuration
     * @param auditor Implements Auditor.write() interface
     */
    void registerAuditor(String name, Auditor auditor);

    AuthorizationHook getDefaultQueryPreauthorizationHook();

    AuthorizationHook getDefaultQueryAuthorizationHook();

    AuthorizationHook getDefaultMutationPreauthorizationHook();

    AuthorizationHook getDefaultMutationAuthorizationHook();

    Authenticator getAuthenticator();

    SessionManager getSessionManager();

    AuditServer getAuditServer();

    AuthConfig getConfig();

    AuthDataSource getAuthDataSource() throws BigDBException;

    ApplicationRegistry getApplicationAuthenticator();
}
