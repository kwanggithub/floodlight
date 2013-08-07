package org.projectfloodlight.db.auth;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.hook.AuthorizationHook;

/**
 * A stub authorization hook that ACCEPTs every authorization request,
 * regardless of whether or not the user has been authenticated. If this hook
 * is registered on the root data node as a preauthorization hook, then
 * authorization is effectively disabled.
 *
 * This would typically only be used during development and not in production.
 *
 * @author Rob Vaterlaus <rob.vaterlaus@bigswitch.com>
 */
public class NullAuthorizationHook implements AuthorizationHook {

    @Override
    public Result authorize(Context context) throws BigDBException {
        return Result.ACCEPT;
    }
}
