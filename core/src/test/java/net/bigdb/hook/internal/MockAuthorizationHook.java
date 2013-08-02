package net.bigdb.hook.internal;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.hook.AuthorizationHook;

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
