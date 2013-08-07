package org.projectfloodlight.db.hook;

import org.projectfloodlight.db.BigDBException;

/** collection of general purpose authorization hooks.
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public final class AuthorizationHooks {
    private AuthorizationHooks() { }

    /** an authorization hook that accepts every request */
    private static class AcceptAuthorizationHook implements AuthorizationHook {
        @Override
        public Result authorize(Context context) throws BigDBException {
            return Result.ACCEPT;
        }
    }
    private static final AuthorizationHook ACCEPT = new AcceptAuthorizationHook();

    /** an authorization hook that rejects every request */
    private static class RejectAuthorizationHook implements AuthorizationHook {
        @Override
        public Result authorize(Context context) throws BigDBException {
            return Result.REJECT;
        }
    }
    private static final AuthorizationHook REJECT = new RejectAuthorizationHook();

    /** return an authorization hook that accepts every request. Instance can be shared */
    public static AuthorizationHook accept() {
        return ACCEPT;
    }

    /** return an authorization hook that rejects every request. Instance can be shared */
    public static AuthorizationHook reject() {
        return REJECT;
    }
}
