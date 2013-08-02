package net.bigdb.auth;

import net.bigdb.BigDBException;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.AuthorizationHook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Global Role Based Platform preauthorization hook.
 * <ul>
 * <li>Allows all operations...
 * <ul>
 * <li>Accepts requests with the system context</li>
 * <li>Accepts request for global admin users</li>
 * <li>Undecided otherwise</li>
 * </ul>
 * </li>
 * </ul>
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 * @author rob.vaterlaus@bigswitch.com
 */
public class PlatformPreauthorizationHook implements AuthorizationHook {

    private final static Logger logger = LoggerFactory
            .getLogger(PlatformPreauthorizationHook.class);

    @Override
    public Result authorize(Context hookContext) throws BigDBException {

        AuthContext authContext = hookContext.getAuthContext();
        AuthorizationHook.Operation operation = hookContext.getOperation();

        AuthorizationHook.Result result;
        String reason;
        if (authContext == null) {
            // not authenticated
            reason = "not authenticated";
            result = AuthorizationHook.Result.REJECT;
        } else if (authContext == AuthContext.SYSTEM) {
            reason = "system";
            result = AuthorizationHook.Result.ACCEPT;
        } else if (authContext.getSessionData() != null && authContext.getUser().isAdmin()) {
            reason = "global admin";
            result = AuthorizationHook.Result.ACCEPT;
        } else if (authContext.getApplication() != null) {
            reason = "unknown application";
            result = AuthorizationHook.Result.UNDECIDED;
        } else {
            reason = "not admin";
            result = AuthorizationHook.Result.UNDECIDED;
        }

        if (logger.isDebugEnabled()) {
            LocationPathExpression hookPath = hookContext.getHookPath();
            logger.debug("Platform preauthorization result for op " + operation +
                    " on "+ hookPath + ": " + result + " (Reason: " + reason +
                    ")");
        }

        return result;
    }

}
