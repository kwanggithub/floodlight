package net.bigdb.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;
import net.bigdb.auth.AuthContext;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.query.Query;
import net.bigdb.query.Step;
import net.bigdb.schema.AggregateSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.SchemaNode;

/**
 * Implementation of a dictionary data node that invokes dynamic data hooks to
 * populate the contents of the node. The dynamic data hook returns an
 * application-defined object (i.e. not a data node). That object is mapped to
 * a data node using the data node mapper.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class DynamicDictionaryDataNode extends AbstractDictionaryDataNode {
    // FIXME: Currently there's a fair bit of code duplication between this
    // class and DynamicKeyedListDataNode. Should refactor to reduce the
    // duplication.

    /** The schema node corresponding to this data node */
    private final AggregateSchemaNode schemaNode;

    /** The data source that this data node belongs to */
    private final DynamicDataSource dataSource;

    /**
     * The operation type that this dynamic data node is servicing. Currently
     * we create new dynamic data nodes for each individual request.
     * FIXME: Do we really need to make the data node operation-specific or
     * can we infer it from context? Otherwise we need separate root data.
     */
    private final DynamicDataHook.Operation operation;

    /**
     * Dynamic node representing the registered dynamic data hooks (and
     * descendant dynamic data hooks) corresponding to this data node.
     */
    private final DynamicNode dynamicNode;

    /** The location path of this data node */
    private final LocationPathExpression locationPath;

    /**
     * The top-level query specified in the request that initiated the call
     * into the dynamic data source and corresponding dynamic data nodes.
     */
    private final Query query;

    /**
     * The data node corresponding to the contributions from all of the
     * registered dynamic data hooks as well as any contributions inherited
     * from the parent data node.
     */
    private DataNode dataNode;

    /** The auth context corresponding to the current request */
    private final AuthContext authContext;

    /**
     * The request properties corresponding to the current request. These
     * are used to share state across multiple invocations of different
     * dynamic data hooks across the duration of a single request
     */
    private final Map<String, Object> requestProperties;

    public DynamicDictionaryDataNode(SchemaNode schemaNode,
            DynamicDataSource dataSource, DynamicDataHook.Operation operation,
            DynamicNode dynamicNode, LocationPathExpression locationPath,
            Query query, Set<DataNode> parentContributions,
            AuthContext authContext, Map<String, Object> requestProperties)
            throws BigDBException {

        super();

        assert schemaNode != null;
        assert schemaNode instanceof AggregateSchemaNode;
        assert dataSource != null;
        assert operation != null;
        assert dynamicNode != null;
        assert locationPath != null;
        assert query != null;
        assert requestProperties != null;

        this.schemaNode = (AggregateSchemaNode) schemaNode;
        this.dataSource = dataSource;
        this.operation = operation;
        this.dynamicNode = dynamicNode;
        this.locationPath = locationPath;
        this.query = query;
        this.authContext = authContext;
        this.requestProperties = requestProperties;

        Set<DataNode> dataNodes = new HashSet<DataNode>();
        if (parentContributions != null)
            dataNodes.addAll(parentContributions);

        // The same dynamic node is used for a list data node and its list
        // elements. The associated data hooks are intended to return the
        // data for the entire list, not for individual list elements, so
        // we suppress calling the data hooks for list elements here.
        // FIXME: Ideally shouldn't have to check node type here. Should
        // come up with a better way to handle this
        if (schemaNode.getNodeType() != SchemaNode.NodeType.LIST_ELEMENT) {
            Iterable<DynamicDataHook> dynamicDataHooks =
                    dynamicNode.getDynamicDataHooks(operation);
            DynamicDataHookContextImpl context =
                    new DynamicDataHookContextImpl(operation, locationPath,
                            query, null, authContext, requestProperties);
            for (DynamicDataHook hook : dynamicDataHooks) {
                Object object = hook.doDynamicData(context);
                if (object != null) {
                    DataNode contribution =
                            DataNodeMapper.getDefaultMapper()
                                    .convertObjectToDataNode(object, schemaNode);
                    if (!contribution.isNull())
                        dataNodes.add(contribution);
                }
            }
        }

        switch (dataNodes.size()) {
        case 0:
            this.dataNode = DataNode.NULL;
            break;
        case 1:
            this.dataNode = dataNodes.iterator().next();
            break;
        default:
            this.dataNode =
                    CompoundDictionaryDataNode.from(schemaNode, dataNodes);
            break;
        }
    }

    @Override
    public DataNode.NodeType getNodeType() {
        switch (schemaNode.getNodeType()) {
        case CONTAINER:
            return DataNode.NodeType.CONTAINER;
        case LIST_ELEMENT:
            return DataNode.NodeType.LIST_ELEMENT;
        default:
            throw new BigDBInternalError(
                    "Expected container or list element node");
        }
    }

    @Override
    public DigestValue computeDigestValue() {
        // Computing the digest values for filter data nodes would be
        // expensive so for now we just don't support it.
        // We'll see if it's needed.
        return null;
    }

    @Override
    public DataNode getChild(Step step) throws BigDBException {
        String name = step.getName();
        DataNode childDataNode = dataNode.getChild(step);

        DynamicNode childDynamicNode = dynamicNode.getChildDynamicNodes().get(name);
        if (childDynamicNode == null)
            return childDataNode;

        SchemaNode childSchemaNode = schemaNode.getChildSchemaNode(name);

        Set<DataNode> parentContributions =
                childDataNode.isNull() ? Collections.<DataNode> emptySet()
                        : Collections.singleton(childDataNode);
        LocationPathExpression childLocationPath =
                locationPath.getChildLocationPath(step);

        DataNode dynamicDataNode;

        switch (childSchemaNode.getNodeType()) {
        case CONTAINER:
            dynamicDataNode =
                    new DynamicDictionaryDataNode(childSchemaNode, dataSource,
                            operation, childDynamicNode, childLocationPath,
                            query, parentContributions, authContext,
                            requestProperties);
            break;
        case LIST:
            ListSchemaNode listSchemaNode = (ListSchemaNode) childSchemaNode;
            if (!listSchemaNode.getKeyNodeNames().isEmpty()) {
                dynamicDataNode =
                        new DynamicKeyedListDataNode(childSchemaNode,
                                dataSource, operation, childDynamicNode,
                                childLocationPath, query,
                                parentContributions, authContext,
                                requestProperties);
                break;
            }

            // Unkeyed lists must come from a single source, so they are
            // handled the same way that leaf and leaf-list nodes are handled.
            // So we just fall through to the code below.

        case LEAF_LIST:
        case LEAF:
            Iterable<DynamicDataHook> childDynamicDataHooks =
                childDynamicNode.getDynamicDataHooks(operation);
            DynamicDataHookContextImpl context =
                    new DynamicDataHookContextImpl(operation, childLocationPath,
                            query, null, authContext, requestProperties);
            Iterator<DynamicDataHook> iter = childDynamicDataHooks.iterator();
            DynamicDataHook childDynamicDataHook = iter.next();
            if (iter.hasNext() || !childDataNode.isNull())
                throw new BigDBException(
                        "Dynamic leaf and leaf nodes must come from a single source");
            Object object = childDynamicDataHook.doDynamicData(context);
            dynamicDataNode =
                    DataNodeMapper.getDefaultMapper().convertObjectToDataNode(
                            object, childSchemaNode);
            break;
        default:
            throw new BigDBInternalError("Invalid dynamic data node type");
        }

        return dynamicDataNode;
    }

    @Override
    protected Set<String> getAllChildNames() {
        Set<String> result = new TreeSet<String>();
        for (SortedMap.Entry<String, SchemaNode> entry : schemaNode
                .getChildNodes().entrySet()) {
            if (entry.getValue().getDataSources()
                    .contains(dataSource.getName())) {
                SchemaNode childSchemaNode = entry.getValue();
                boolean cascade =
                        childSchemaNode.getBooleanAttributeValue(
                                SchemaNode.CASCADE_ATTRIBUTE_NAME, true);
                if (cascade) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }
}
