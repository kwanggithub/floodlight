package net.bigdb.hook.internal;

import net.bigdb.data.DataNode;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.ValidationHook;

public class ValidationHookContextImpl implements ValidationHook.Context {

    private final DataNode rootDataNode;
    private LocationPathExpression hookPath;
    private boolean listValidation;
    private DataNode hookDataNode;

    public ValidationHookContextImpl(DataNode rootDataNode) {
        this.rootDataNode = rootDataNode;
    }

    @Override
    public LocationPathExpression getHookPath() {
        return hookPath;
    }

    @Override
    public boolean isListValidation() {
        return listValidation;
    }

    @Override
    public DataNode getHookDataNode() {
        return hookDataNode;
    }

    @Override
    public DataNode getRootDataNode() {
        return rootDataNode;
    }

    public void setHookInfo(LocationPathExpression hookPath,
            boolean listValidation, DataNode hookDataNode) {
        this.hookPath = hookPath;
        this.listValidation = listValidation;
        this.hookDataNode = hookDataNode;
    }
}
