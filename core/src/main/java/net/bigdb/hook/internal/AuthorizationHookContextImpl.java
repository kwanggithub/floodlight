package net.bigdb.hook.internal;

import net.bigdb.auth.AuthContext;
import net.bigdb.data.DataNode;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.AuthorizationHook;
import net.bigdb.schema.SchemaNode;

/**
 * Simple implementation of the authorization context interface.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public final class AuthorizationHookContextImpl implements
        AuthorizationHook.Context {

    private final AuthorizationHook.Operation operation;
    private final DataNode oldRootDataNode;
    private final DataNode newRootDataNode;
    private final DataNode writtenRootDataNode;
    private final AuthContext authContext;
    private AuthorizationHook.Stage stage;
    private LocationPathExpression hookPath;
    private SchemaNode hookSchemaNode;
    private DataNode oldHookDataNode;
    private DataNode newHookDataNode;
    private DataNode writtenHookDataNode;

    public AuthorizationHookContextImpl(AuthorizationHook.Operation operation,
            DataNode oldRootDataNode, DataNode newRootDataNode,
            DataNode writtenRootDataNode, AuthContext authContext) {
        this.operation = operation;
        this.oldRootDataNode = oldRootDataNode;
        this.newRootDataNode = newRootDataNode;
        this.writtenRootDataNode = writtenRootDataNode;
        this.authContext = authContext;
    }

    @Override
    public AuthorizationHook.Operation getOperation() {
        return operation;
    }

    @Override
    public AuthorizationHook.Stage getStage() {
        return stage;
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
    public DataNode getWrittenRootDataNode() {
        return writtenRootDataNode;
    }

    @Override
    public AuthContext getAuthContext() {
        return authContext;
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
    public DataNode getWrittenHookDataNode() {
        return writtenHookDataNode;
    }

    public void setStage(AuthorizationHook.Stage stage) {
        this.stage = stage;
    }

    public void setHookInfo(LocationPathExpression hookPath,
            SchemaNode hookSchemaNode, DataNode oldHookDataNode,
            DataNode newHookDataNode, DataNode writtenHookDataNode) {
        this.hookPath = hookPath;
        this.hookSchemaNode = hookSchemaNode;
        this.oldHookDataNode = oldHookDataNode;
        this.newHookDataNode = newHookDataNode;
        this.writtenHookDataNode = writtenHookDataNode;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean details) {
        return "AuthorizationHookContextImpl [operation=" + operation
                +
                ( details ?
                ", oldRootDataNode=" + oldRootDataNode + ", newRootDataNode="
                + newRootDataNode + ", writtenRootDataNode=" + writtenRootDataNode
                 :
                     ""
                )
                + ", authContext=" + authContext + ", stage=" + stage + ", hookPath="
                + hookPath + ", hookSchemaNode=" + hookSchemaNode + ", oldHookDataNode="
                +
                ( details ?
                   oldHookDataNode + ", newHookDataNode=" + newHookDataNode
                   + ", writtenHookDataNode=" + writtenHookDataNode
                :
                    ""
               )
                + "]";
    }

}
