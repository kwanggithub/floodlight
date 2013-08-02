package net.bigdb.auth;

import java.io.IOException;

import net.bigdb.BigDBException;
import net.bigdb.auth.simpleacl.SimpleACLAuthorizationHook;
import net.bigdb.auth.simpleacl.SimpleACLRecorder;
import net.bigdb.hook.AuthorizationHook;

/**
 * An authorization hook implementation that allows access to a restriced set of
 * operations considered globally 'safe' and necessary for basic work.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 * @author Rob Vaterlaus <rob.vaterlaus@bigswitch.com>
 */
public class RestrictedAuthorizationHook implements AuthorizationHook {

    private final AuthorizationHook delegate;

    public RestrictedAuthorizationHook(AuthConfig config) {
        boolean record = config.getParam(AuthConfig.RESTRICTED_POLICY_RECORD);
        String policyFile = config.getParam(AuthConfig.RESTRICTED_POLICY_FILE);
        AuthorizationHook.Result defaultResult =
                config.getParam(AuthConfig.RESTRICTED_POLICY_DEFAULT_RESULT);

        try {
            if (!record) {
                SimpleACLAuthorizationHook simpleAuthorizationHook =
                        new SimpleACLAuthorizationHook(defaultResult);
                simpleAuthorizationHook.load(policyFile);
                this.delegate = simpleAuthorizationHook;
            } else {
                delegate = new SimpleACLRecorder(defaultResult, policyFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Loading restricted policy " + policyFile + ": "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Result authorize(Context context) throws BigDBException {
        if (context.getAuthContext() == AuthContext.SYSTEM)
            return AuthorizationHook.Result.ACCEPT;

        return delegate.authorize(context);
    }

}
