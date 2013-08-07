package org.projectfloodlight.db.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode.DataNodeWithPath;
import org.projectfloodlight.db.data.memory.MemoryDataNodeFactory;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.schema.Schema;
import org.projectfloodlight.db.service.BigDBOperation;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.db.service.internal.TreespaceImpl;
import org.projectfloodlight.db.util.Path;

import com.google.common.collect.ImmutableList;

/**
 * Data source implementation that invokes hooks registered by the application
 * code to populate nodes in the BigDB tree. Application code hooks into the
 * dynamic data tree by implementing the DynamicDataHook interface and
 * registering the hooks with the dynamic data source using the
 * registerDynamicDataHook method.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class DynamicDataSource extends AbstractDataSource implements TreespaceAware {

    /**
     * The root of the dynamic node tree. The layout of the dynamic node tree
     * mirrors the layout of the schema tree, except that it is pruned to only
     * contain the nodes where dynamic data hooks have been registered (or if
     * hooks have been registered with descendent nodes). Dynamic nodes are
     * created on demand as dynamic data hooks are registered with the dynamic
     * data source.
     */
    private DynamicNode rootDynamicNode = null;
    private volatile TreespaceImpl treespace;

    public DynamicDataSource(String name, boolean config, Schema schema) {
        super(name, config, schema, new MemoryDataNodeFactory());
        try {
            rootDynamicNode = new DynamicNode(schema.getSchemaNode(LocationPathExpression.ROOT_PATH));
        } catch (BigDBException e) {
            throw new IllegalArgumentException("Could not lookup root schema node", e);
        }
    }

    @Override
    public DataNode getRoot() throws BigDBException {
        // No query specified so just use the root query
        return getRoot(AuthContext.SYSTEM, Query.ROOT_QUERY);
    }

    /**
     * Get the root of the dynamic data source tree. Currently a new instance
     * of a dynamic dictionary data node is created for every request. The
     * query is passed in, because the query is part of the DynamicDataHook
     * interface and can be used by the hook code to optimize the results that
     * are returned.
     *
     * @param authContext the auth context for the current request
     * @param query the query for the current request
     * @return a data node representing the root of the dynamic data tree
     * @throws BigDBException
     */
    @Override
    public DataNode getRoot(AuthContext authContext,
            Query query) throws BigDBException {
        Map<String, Object> requestProperties = new HashMap<String, Object>();
        return getRootPrivate(authContext, query, requestProperties);
    }

    /**
     * Internal method for constructing the root of the dynamic data tree. The
     * additional arguments are things that are exposed in the
     * @param authContext
     * @param query
     * @param requestProperties
     * @return
     * @throws BigDBException
     */
    private DynamicDictionaryDataNode getRootPrivate(AuthContext authContext,
            Query query, Map<String, Object> requestProperties)
            throws BigDBException {
        // FIXME: Hard-code operation to QUERY for now.
        return new DynamicDictionaryDataNode(getRootSchemaNode(), this,
                DynamicDataHook.Operation.QUERY, rootDynamicNode,
                LocationPathExpression.ROOT_PATH, query, Collections
                        .<DataNode> emptySet(), authContext, requestProperties);
    }

    /**
     * Register a dynamic data hook with the dynamic data source at the
     * specified path for the specified operations. The hook will be called
     * whenever a request is made whose operation type is in the operations set
     * and whose query path traverses the path where the hook is registered.
     *
     * @param path
     *            the path in the tree where the dynamic data hook is registered
     * @param dynamicDataHook
     *            the instance of a dynamic data hook to be invoked
     * @param operations
     *            the set of operations for which the hook should be invoked
     */
    public void registerDynamicDataHook(Path path,
            DynamicDataHook dynamicDataHook,
            Set<DynamicDataHook.Operation> operations) {
        rootDynamicNode.addDescendentHook(path, dynamicDataHook, operations);
    }

    @Override
    public Iterable<DataNodeWithPath> queryData(Query query,
            AuthContext authContext) throws BigDBException {
        DataNode rootDataNode = getRoot(authContext, query);
        Iterable<DataNodeWithPath> queryResult =
                rootDataNode.queryWithPath(getRootSchemaNode(),
                        query.getBasePath(), true);
        return queryResult;
    }

    DynamicDataHook.Operation bigDBOperationToDynamicDataHookOperation(
            BigDBOperation operation) {
        switch (operation) {
        case DELETE:
            return DynamicDataHook.Operation.DELETE;
        case QUERY:
            return DynamicDataHook.Operation.QUERY;
        case INSERT:
            return DynamicDataHook.Operation.INSERT;
        case REPLACE:
            return DynamicDataHook.Operation.REPLACE;
        case UPDATE:
            return DynamicDataHook.Operation.UPDATE;
        default:
            throw new BigDBInternalError("Unknown BigDBOperation");
        }
    }
    /**
     * Common helper function that's invoked for all mutation operations. The
     * logic is the same for replace vs. update vs. delete operations. The only
     * difference is the lookup for the registered hooks and the operation type
     * that's specified in the dynamic data hook context.
     *
     * @param operation the operation type for the current request
     * @param query the query for the current request
     * @param data the mutation data for the current request (null for delete)
     * @param authContext the auth context for the current request
     * @throws BigDBException
     */
    private void mutateData(BigDBOperation operation, Query query,
            DataNode data, AuthContext authContext) throws BigDBException {

        // This is a temporary implementation pending the refactoring of how
        // data node mutation is handled. With the proposed refactoring the
        // idea is to make data nodes strictly immutable but have mutation
        // methods that return new data node instance representing the
        // mutated result. With that refactoring the mutation logic would go
        // in the dynamic data node classes themselves rather than here in the
        // dynamic data source. In that case this method would really just
        // forward the mutation request to the root data node (or possibly go
        // away altogether).

        DynamicDataHook.Operation dynamicDataOperation =
                bigDBOperationToDynamicDataHookOperation(operation);

        // Determine the dynamic node corresponding to the query
        LocationPathExpression basePath = query.getBasePath();
        DynamicNode dynamicNode = rootDynamicNode.getDescendentDynamicNode(
                basePath.getSimplePath());

        // FIXME: Should it be an error if there's not a dynamic data node
        // corresponding to the query path? If that happens isn't that
        // indicative of a mismatch between the schema and the application code
        // that registers the dynamic data hooks. Need to investigate some more
        // to determine if that's true in all cases.

        Iterable<DynamicDataHook> dataHooks = null;
        if (dynamicNode != null) {
            // Look up the data hooks corresponding to the current operation
            dataHooks = dynamicNode.getDynamicDataHooks(dynamicDataOperation);
        }

        if ((dataHooks == null) || !dataHooks.iterator().hasNext()) {
            // FIXME: What should this do? Should it be an error if there's no
            // handler for the mutation operation or is it OK to just noop it?
            // It breaks a bunch of BVS tests if we throw the exception here,
            // though, so for now we'll just noop.
//            throw new BigDBException(String.format(
//                    "Operation %s not supported for path \"%s\"", operation, basePath));
            return;
        }

        boolean expandTrailingList = ((operation == BigDBOperation.DELETE) ||
                (operation == BigDBOperation.UPDATE));
        // Query for the data nodes that match the input query
        Iterable<DataNodeWithPath> dataNodes =
                treespace.performQuery(
                query, operation, ImmutableList.<DataSource>of(this),
                authContext, expandTrailingList);

        // Extract the list of paths from the query results into a separate
        // list. We do that because it's possible that the iterator returned
        // from a dynamic data hook could be invalidated by the mutation
        // operation performed belov (e.g. a delete).
        List<LocationPathExpression> mutatedPaths =
                new ArrayList<LocationPathExpression>();
        for (DataNodeWithPath dataNodeWithPath: dataNodes) {
            mutatedPaths.add(dataNodeWithPath.getPath());
        }

        // Invoke the data hooks for all of the matching data nodes
        Map<String, Object> requestProperties = new HashMap<String, Object>();
        for (LocationPathExpression mutatedPath: mutatedPaths) {
            for (DynamicDataHook dataHook: dataHooks) {
                DynamicDataHookContextImpl context =
                        new DynamicDataHookContextImpl(
                                dynamicDataOperation, mutatedPath,
                                query, data, authContext,
                                requestProperties);
                // FIXME: Don't ignore result
                //Object object = dataHook.doDynamicData(context);
                dataHook.doDynamicData(context);
            }
        }
    }

    @Override
    public void
            insertData(Query query, DataNode data, AuthContext authContext)
                    throws BigDBException {

        mutateData(BigDBOperation.INSERT, query, data, authContext);
    }

    @Override
    public void
            replaceData(Query query, DataNode data, AuthContext authContext)
                    throws BigDBException {

        mutateData(BigDBOperation.REPLACE, query, data, authContext);
    }

    @Override
    public void updateData(Query query, DataNode data,
            AuthContext authContext) throws BigDBException {
        mutateData(BigDBOperation.UPDATE, query, data, authContext);
    }

    @Override
    public void
            deleteData(Query query, AuthContext authContext)
                    throws BigDBException {
        mutateData(BigDBOperation.DELETE, query, null, authContext);
    }

    /** This is a temporary hack while DynamicDataSource needs access to treespace */
    @Override
    public synchronized void setTreespace(Treespace treespace) {
        if(getState() != State.CREATED)
            throw new IllegalStateException("Illegal state for treespace configuration: DataSource is not CREATED");

        if(!(treespace instanceof TreespaceImpl))
            throw new IllegalArgumentException("Require instance of TreespaceImpl");

        // FIXME: Get rid of the cast to TreespaceImpl. Should refactor the
        // code so that performQuery isn't a TreespaceImpl method.

        this.treespace = (TreespaceImpl) treespace;
    }


}
