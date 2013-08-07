package org.projectfloodlight.db.data;

import java.util.Map;

import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;

/**
 * Implementation of the DynamicDataHook context interface
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class DynamicDataHookContextImpl implements DynamicDataHook.Context {

    private final DynamicDataHook.Operation operation;
    private final LocationPathExpression hookPath;
    private final Query query;
    private final DataNode mutationDataNode;
    private final AuthContext authContext;
    private final Map<String, Object> requestProperties;

    public DynamicDataHookContextImpl(DynamicDataHook.Operation operation,
            LocationPathExpression hookPath, Query query,
            DataNode mutationDataNode, AuthContext authContext,
            Map<String, Object> requestProperties) {
        this.operation = operation;
        this.hookPath = hookPath;
        this.query = query;
        this.mutationDataNode = mutationDataNode;
        this.authContext = authContext;
        this.requestProperties = requestProperties;
    }

    @Override
    public DynamicDataHook.Operation getOperation() {
        return operation;
    }

    @Override
    public LocationPathExpression getHookPath() {
        return hookPath;
    }

    @Override
    public Query getQuery() {
        return query;
    }

    @Override
    public DataNode getMutationDataNode() {
        return mutationDataNode;
    }

    @Override
    public AuthContext getAuthContext() {
        return authContext;
    }

    @Override
    public Object getRequestProperty(String name) {
        return requestProperties.get(name);
    }

    @Override
    public void setRequestProperty(String name, Object value) {
        requestProperties.put(name, value);
    }
}
