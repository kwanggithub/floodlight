package net.bigdb.hook.internal;

import net.bigdb.auth.AuthContext;
import net.bigdb.data.DataNode;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.FilterHook;

public class FilterHookContextImpl implements FilterHook.Context {

    private final FilterHook.Operation operation;
    private final LocationPathExpression hookPath;
    private final DataNode hookDataNode;
    private final AuthContext authContext;

    public FilterHookContextImpl(FilterHook.Operation operation,
            LocationPathExpression hookPath, DataNode hookDataNode,
            AuthContext authContext) {
        this.operation = operation;
        this.hookPath = hookPath;
        this.hookDataNode = hookDataNode;
        this.authContext = authContext;
    }

    @Override
    public FilterHook.Operation getOperation() {
        return operation;
    }

    @Override
    public LocationPathExpression getHookPath() {
        return hookPath;
    }

    @Override
    public DataNode getHookDataNode() {
        return hookDataNode;
    }

    @Override
    public AuthContext getAuthContext() {
        return authContext;
    }

    @Override
    public String toString() {
        return "FilterHookContextImpl [operation=" + operation + ", hookPath=" + hookPath
                + ", hookDataNode=" + hookDataNode + ", authContext=" + authContext + "]";
    }

}
