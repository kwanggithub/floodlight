package net.bigdb.auth;

import java.io.File;

import net.bigdb.BigDBException;
import net.bigdb.auth.application.ApplicationRegistry;
import net.bigdb.auth.application.ApplicationRegistryConfig;
import net.bigdb.auth.password.BigDBHashPasswordResource;
import net.bigdb.auth.password.PasswordHasher;
import net.bigdb.auth.session.BigDBSessionResource;
import net.bigdb.auth.session.SessionManager;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.AuthorizationHook;
import net.bigdb.hook.AuthorizationHooks;
import net.bigdb.schema.Schema;
import net.bigdb.service.Service;
import net.bigdb.service.Treespace;
import net.bigdb.util.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

/** Implementation of the authentication subservice. Registers global authenticators, authorizers,
 *  adds the dynamic resources to the AuthDataSource.
 * <p>
 * Manages multiple authentication sources via an instance of
 * DynamicAuthenticator.  Exposes its registration function here.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class AuthServiceImpl implements AuthService {
    private final static Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AuthorizationHook defaultQueryPreauthorizationHook;
    private final AuthorizationHook defaultQueryAuthorizationHook;
    private final AuthorizationHook defaultMutationPreauthorizationHook;
    private final AuthorizationHook defaultMutationAuthorizationHook;
    private final DynamicAuthenticator authenticator;
    private final LocalAuthenticator localAuthenticator;
    private final SessionManager sessionManager;
    private final Service service;
    private AuthDataSource authDataSource;
    private final AuthConfig config;
    private final BigDBAuthenticatorRegistry registry;
    private final AuditServer auditServer;
    private final BigDBAuditorRegistry auditorRegistry;

    private final PasswordHasher passwordHasher;

    private final File passwdFile;

    private final ApplicationRegistry applicationAuthenticator;

    public AuthServiceImpl(Service service, AuthConfig config) throws BigDBException {
        logger.info("Initializing auth service with auth config "+config);

        this.service = service;
        this.config = config;
        defaultQueryPreauthorizationHook = AuthComponentCreator.create(config.getParam(AuthConfig.DEFAULT_QUERY_PREAUTHORIZATION_HOOK), config);
        defaultQueryAuthorizationHook = AuthComponentCreator.create(config.getParam(AuthConfig.DEFAULT_QUERY_AUTHORIZATION_HOOK), config);
        defaultMutationPreauthorizationHook = AuthComponentCreator.create(config.getParam(AuthConfig.DEFAULT_MUTATION_PREAUTHORIZATION_HOOK), config);
        defaultMutationAuthorizationHook = AuthComponentCreator.create(config.getParam(AuthConfig.DEFAULT_MUTATION_AUTHORIZATION_HOOK), config);
        sessionManager = AuthComponentCreator.create(config.getParam(AuthConfig.SESSION_MANAGER), config);
        passwordHasher = AuthComponentCreator.create(config.getParam(AuthConfig.PASSWORD_HASHER), config);
        localAuthenticator = new LocalAuthenticator(service, passwordHasher);
        registry = new BigDBAuthenticatorRegistry(service);
        registry.registerAuthenticator(LocalAuthenticator.NAME, localAuthenticator);
        authenticator = new DynamicAuthenticator(service, localAuthenticator, registry);
        auditServer = AuditServer.getInstance();

        passwdFile = config.getParam(AuthConfig.PASSWD_FILE);

        /* start audit service based on configuration */
        auditorRegistry = new BigDBAuditorRegistry(service);
        auditorRegistry.registerAuditor("local", auditServer.getDefaultAuditor());
        auditServer.setRegistry(auditorRegistry);

        /* bootstrap application keys from configuration data source in properties */
        applicationAuthenticator = ApplicationRegistryConfig.fromAuthConfig(config);
    }

    public synchronized void setSchema(Schema schema) throws BigDBException {
        authDataSource = new AuthDataSource(schema);
    }

    @Override
    public void init() throws BigDBException {
        // In floodlight module loading context, this is called
        // in init phase (before modules are started up.
    }

    @Override
    public void startUp() throws BigDBException {
        String adminPassword = config.getParam(AuthConfig.RESET_ADMIN_PASSWORD);
        if (!Strings.isNullOrEmpty(adminPassword)) {
            authenticator.resetAdminPassword(adminPassword);
        }

        // router for 'core/aaa' already constructed, attach it here
        Path basePath = new Path("/core/aaa");

        AuthDataSource authDataSource = getAuthDataSource();

        Treespace treespace = service.getTreespace("controller");
        if (treespace == null)
            throw new BigDBException("Controller treespace not found");

        authDataSource.registerDynamicDataHooksFromObject(
                basePath, new Path("session"),
                new BigDBSessionResource(sessionManager));

        // access control for BigDBSessionResource
        LocationPathExpression sessionPath =
                LocationPathExpression.parse("/core/aaa/session");
        treespace.getHookRegistry().registerFilterHook(sessionPath,
                new BigDBSessionResource.SessionFilterHook());
        treespace.getHookRegistry().registerAuthorizationHook(sessionPath,
                AuthorizationHook.Operation.MUTATION,
                AuthorizationHook.Stage.AUTHORIZATION, true,
                AuthorizationHooks.accept());
        treespace.getHookRegistry().registerAuthorizationHook(sessionPath,
                AuthorizationHook.Operation.QUERY,
                AuthorizationHook.Stage.AUTHORIZATION, true,
                AuthorizationHooks.accept());

        treespace.getHookRegistry().registerValidationHook(
                     LocationPathExpression.parse("/core/aaa/group"), true,
                     new LocalGroupValidationHook(treespace));
        LocationPathExpression localUserPath =
                LocationPathExpression.parse("/core/aaa/local-user");
        treespace.getHookRegistry().registerValidationHook(
                localUserPath, true, new LocalAdminUserValidationHook());
        treespace.getHookRegistry().registerWatchHook(
                localUserPath, false, new LocalUserWatchHook(treespace));
        if (!passwdFile.toString().equals(""))
            treespace.getHookRegistry().registerValidationHook(
                    LocationPathExpression.parse("/core/aaa/local-user"), false,
                    new UnixUserValidationHook(passwdFile));

        authDataSource.registerDynamicDataHooksFromObject(
                basePath, new Path("method"),
                new BigDBMethodResource(registry));
        authDataSource.registerDynamicDataHooksFromObject(
                basePath, new Path("hash-password"),
                new BigDBHashPasswordResource(passwordHasher));

        BigDBAuditEventResource auditResource = new BigDBAuditEventResource();
        authDataSource.registerDynamicDataHooksFromObject(basePath, new Path("audit-event"), auditResource);
        AuthorizationHook auditHook = new BigDBAuditEventResource.AuthHook(applicationAuthenticator);
        treespace.getHookRegistry().registerAuthorizationHook(LocationPathExpression.parse("/core/aaa/audit-event"),
                                                              AuthorizationHook.Operation.MUTATION,
                                                              AuthorizationHook.Stage.AUTHORIZATION,
                                                              true,
                                                              auditHook);
        authDataSource.registerDynamicDataHooksFromObject(
                basePath, new Path("change-password-local-user"),
                new LocalUserPasswordChangeResource(localAuthenticator));
        treespace.getHookRegistry().registerAuthorizationHook(
                LocationPathExpression.parse("/core/aaa/change-password-local-user"),
                AuthorizationHook.Operation.MUTATION,
                AuthorizationHook.Stage.AUTHORIZATION, false,
                AuthorizationHooks.accept());
    }

    @Override
    public void registerAuthenticator(String name, AuthenticatorMethod authn) {
        registry.registerAuthenticator(name, authn);
    }

    @Override
    public AuthorizationHook getDefaultQueryPreauthorizationHook() {
        return defaultQueryPreauthorizationHook;
    }

    @Override
    public AuthorizationHook getDefaultQueryAuthorizationHook() {
        return defaultQueryAuthorizationHook;
    }

    @Override
    public AuthorizationHook getDefaultMutationPreauthorizationHook() {
        return defaultMutationPreauthorizationHook;
    }

    @Override
    public AuthorizationHook getDefaultMutationAuthorizationHook() {
        return defaultMutationAuthorizationHook;
    }

    @Override
    public Authenticator getAuthenticator() {
        return authenticator;
    }

    @Override
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @Override
    public AuthConfig getConfig() {
        return config;
    }

    @Override
    public void registerAuditor(String name, Auditor auditor) {
        auditorRegistry.registerAuditor(name, auditor);
    }

    @Override
    public AuditServer getAuditServer() {
        return auditServer;
    }

    public BigDBAuditorRegistry getAuditorRegistry() {
        return auditorRegistry;
    }

    @Override
    public synchronized AuthDataSource getAuthDataSource() throws BigDBException {
        if(authDataSource == null) {
            Treespace treespace = service.getTreespace("controller");
            if (treespace == null)
                throw new BigDBException("Controller treespace not found");

            authDataSource = new AuthDataSource(treespace.getSchema());

            // FIXME: This assumes a single controller treespace. Will need to fix
            // this up if/when we support multiple treespaces. Probably need to
            // have a separate auth data source per treespace.
            authDataSource.setTreespace(treespace);
            treespace.registerDataSource(authDataSource);
        }

        return authDataSource;
    }

    @Override
    public ApplicationRegistry getApplicationAuthenticator() {
        return applicationAuthenticator;
    }
}
