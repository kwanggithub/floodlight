package org.projectfloodlight.db.hook.internal;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.hook.AuthorizationHook;

public class MockAuthorizationHook implements AuthorizationHook {

    private final AuthorizationHook.Result result;
    private int authorizeCallCounter;

    public MockAuthorizationHook(AuthorizationHook.Result result) {
        this.result = result;
    }

    public int getAuthorizeCallCounter() {
        return authorizeCallCounter;
    }

    @Override
    public Result authorize(Context context) throws BigDBException {
        @SuppressWarnings("unused")
        DataNode oldHookDataNode = context.getOldHookDataNode();
        @SuppressWarnings("unused")
        DataNode newHookDataNode = context.getNewHookDataNode();
        authorizeCallCounter++;
        return result;
    }
}
