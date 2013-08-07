package org.projectfloodlight.db.auth;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.hook.AuthorizationHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Global Role Based Platform authorization hook.
 * <ul>
 * <li>Accepts whitelisted 'unrestricted' operations to the rest
 * <li>Rejects otherwise
 * </ul>
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 * @author rob.vaterlaus@bigswitch.com
 */
public class PlatformAuthorizationHook implements AuthorizationHook {

    private final static Logger logger = LoggerFactory
            .getLogger(PlatformAuthorizationHook.class);

    private final RestrictedAuthorizationHook restrictedAuthorizationHook;

    public PlatformAuthorizationHook(AuthConfig authConfig) {
        restrictedAuthorizationHook =
                new RestrictedAuthorizationHook(authConfig);
    }

    @Override
    public Result authorize(Context hookContext) throws BigDBException {
        AuthorizationHook.Result result;
        String reason;
        AuthorizationHook.Operation operation = hookContext.getOperation();

        reason = "decision by restricted authorization hook";
        result = restrictedAuthorizationHook.authorize(hookContext);

        if (logger.isDebugEnabled()) {
            LocationPathExpression hookPath = hookContext.getHookPath();
            logger.debug("Platform authorization result for op " + operation +
            " on "+ hookPath + ": " + result + " (Reason: " + reason + ")");
        }

        return result;
    }
}
