package org.projectfloodlight.db.hook;

import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.hook.WatchHook.Context;
import org.projectfloodlight.db.schema.SchemaNode;

public class WatchHookContextImpl implements Context {

    private final DataNode oldRootDataNode;
    private final DataNode newRootDataNode;
    private final DataNode rootDataNodeDiffs;
    private LocationPathExpression hookPath;
    private SchemaNode hookSchemaNode;
    private DataNode oldHookDataNode;
    private DataNode newHookDataNode;
    private DataNode hookDataNodeDiffs;

    public WatchHookContextImpl(DataNode oldRootDataNode,
            DataNode newRootDataNode, DataNode rootDataNodeDiffs) {
        this.oldRootDataNode = oldRootDataNode;
        this.newRootDataNode = newRootDataNode;
        this.rootDataNodeDiffs = rootDataNodeDiffs;
    }

    @Override
    public DataNode getOldRootDataNode() {
        return oldRootDataNode;
    }

    @Override
    public DataNode getNewRootDataNode() {
        return newRootDataNode;
    }

    @Override
    public DataNode getRootDataNodeDiffs() {
        return rootDataNodeDiffs;
    }

    @Override
    public LocationPathExpression getHookPath() {
        return hookPath;
    }

    @Override
    public SchemaNode getHookSchemaNode() {
        return hookSchemaNode;
    }

    @Override
    public DataNode getOldHookDataNode() {
        return oldHookDataNode;
    }

    @Override
    public DataNode getNewHookDataNode() {
        return newHookDataNode;
    }

    @Override
    public DataNode getHookDataNodeDiffs() {
        return hookDataNodeDiffs;
    }

    public void setHookInfo(LocationPathExpression hookPath,
            SchemaNode hookSchemaNode, DataNode oldHookDataNode,
            DataNode newHookDataNode, DataNode hookDataNodeDiffs) {
        this.hookPath = hookPath;
        this.hookSchemaNode = hookSchemaNode;
        this.oldHookDataNode = oldHookDataNode;
        this.newHookDataNode = newHookDataNode;
        this.hookDataNodeDiffs = hookDataNodeDiffs;
    }

}
