package org.projectfloodlight.db.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.core.IFloodlightProviderService;
import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.core.module.FloodlightModuleException;
import org.projectfloodlight.core.module.IFloodlightModule;
import org.projectfloodlight.core.module.IFloodlightService;
import org.projectfloodlight.db.IBigDBService;
import org.projectfloodlight.db.rest.auth.LoginRequest;
import org.projectfloodlight.db.service.Service;
import org.restlet.data.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dummy implementation of the Authenticator class. Allows users 'santa' and
 * 'claus', each, with password 'hoho'. Santa is an admin, claus is not.
 * <b>Obviously</b>, don't use in production.
 *
 * Note that this particular authn module does *not* directly depend on BigDB.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class DummyAuthenticator implements IFloodlightModule {

    protected final static Logger log = LoggerFactory.getLogger(DummyAuthenticator.class);

    public static class Method implements AuthenticatorMethod {

        public static final String NAME = "dummy";

        private AuthnResult authn(String login, String password)
                throws AuthenticationException {

            if (!("santa".equals(login) || "claus".equals(login)))
                return AuthnResult.notAuthenticated();

            if (password != null) {
                if(!"hoho".equals(password))
                    return AuthnResult.notAuthenticated();
            }
            return AuthnResult.success(login, "Santa Claus");
        }

        private AuthzResult authz(AuthnResult context)
                throws AuthenticationException {

            Set<String> groups;
            if ("santa".equals(context.getPrincipal()))
                groups = Collections.singleton(BigDBGroup.ADMIN.getName());
            else if ("claus".equals(context.getPrincipal()))
                groups = Collections.emptySet();
            else
                return AuthzResult.notAuthorized();

            return AuthzResult.success(groups);
        }

        @Override
        public AuthnResult getPrincipal(LoginRequest request, ClientInfo clientInfo)
                                                throws AuthenticationException {
            return authn(request.getUser(), request.getPassword());
        }

        @Override
        public AuthzResult getGroups(AuthnResult principalContext,
                                     LoginRequest request, ClientInfo clientInfo)
                                             throws AuthenticationException {
            return authz(principalContext);
        }

    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        // no services directly implemented here
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        // no providers exposed here
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IBigDBService.class);
        return l;
    }

    @Override
    public
            void
            init(FloodlightModuleContext context) throws FloodlightModuleException {
        IBigDBService isvc = context.getServiceImpl(IBigDBService.class);
        Service svc = isvc.getService();
        AuthService authService = svc.getAuthService();
        if (authService != null)
            authService.registerAuthenticator("dummy", new Method());
        else
            log.warn("No auth service found -- cannot register ");
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // no startup indicated at module scope
    }

}
