package net.bigdb.data;

import java.util.List;
import java.util.Set;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;
import net.bigdb.auth.AuthContext;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.FilterHook;
import net.bigdb.hook.HookRegistry;
import net.bigdb.hook.internal.FilterHookContextImpl;
import net.bigdb.query.Step;
import net.bigdb.schema.AggregateSchemaNode;
import net.bigdb.schema.InvalidSchemaTypeException;
import net.bigdb.schema.SchemaNode;

/**
 * A dictionary data node implementation that invokes any registered filter
 * hooks to possibly filter/exclude child data nodes.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class FilterDictionaryDataNode extends AbstractDictionaryDataNode {

    /**
     * The schema node corresponding to this data node. This should be either a
     * ContainerSchemaNode or ListElementSchemaNode, both of which are
     * subclasses of AggregateSchemaNode.
     */
    private final AggregateSchemaNode schemaNode;

    /**
     * The underlying data node that's being filtered.
     * FIXME: Ideally this would just be a regular data node and not have to be
     * an AbstractDictionaryDataNode.
     */
    private final AbstractDictionaryDataNode sourceDataNode;

    /** The location path of this data node */
    private final LocationPathExpression locationPath;

    /** The hook registry used to look up the filter hooks */
    private final HookRegistry hookRegistry;

    /** The operation being performed */
    private final FilterHook.Operation operation;

    /** The auth context to be passed to the filter hooks */
    private final AuthContext authContext;

    public FilterDictionaryDataNode(SchemaNode schemaNode,
            DataNode sourceDataNode, LocationPathExpression locationPath,
            HookRegistry hookRegistry, FilterHook.Operation operation,
            AuthContext authContext) {

        super();

        assert schemaNode != null;
        assert schemaNode instanceof AggregateSchemaNode;
        assert sourceDataNode != null;
        assert sourceDataNode instanceof AbstractDictionaryDataNode;
        assert locationPath != null;
        assert hookRegistry != null;
        assert operation != null;

        this.schemaNode = (AggregateSchemaNode) schemaNode;
        this.sourceDataNode = (AbstractDictionaryDataNode) sourceDataNode;
        this.locationPath = locationPath;
        this.hookRegistry = hookRegistry;
        this.operation = operation;
        this.authContext = authContext;
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

        // Look up the child data node. If the child doesn't exists then we
        // don't need to do any filtering/wrapping of the data node
        DataNode childDataNode = sourceDataNode.getChild(step);
        if (childDataNode.isNull())
            return childDataNode;

        // Get the schema node for the requested child. If the name argument
        // doesn't map to a child that's defined in the schema, then this will
        // throw a SchemaNodeNotFoundException.
        SchemaNode childSchemaNode = schemaNode.getChildSchemaNode(name);

        // Get the child location path
        LocationPathExpression childLocationPath =
                locationPath.getChildLocationPath(step);

        if (childSchemaNode.getNodeType() != SchemaNode.NodeType.LIST) {
            // Look up and invoke any registered filter hooks. If any of the hooks
            // excludes the child data node, then we can immediately return NULL.
            List<FilterHook> filterHooks =
                    hookRegistry.getFilterHooks(childLocationPath);
            if (!filterHooks.isEmpty()) {
                FilterHookContextImpl filterHookContext =
                        new FilterHookContextImpl(operation, childLocationPath,
                                childDataNode, authContext);
                for (FilterHook filterHook : filterHooks) {
                    FilterHook.Result result = filterHook.filter(filterHookContext);
                    if (result == FilterHook.Result.EXCLUDE)
                        return DataNode.NULL;
                }
            }
        }

        // If we reach here, then the child node wasn't filtered. Aggregate
        // child data nodes need to be wrapped in another filter data node.
        switch (childSchemaNode.getNodeType()) {
        case CONTAINER:
        case LIST_ELEMENT:  // FIXME: Can this ever be a list element
            return new FilterDictionaryDataNode(childSchemaNode, childDataNode,
                    childLocationPath, hookRegistry, operation, authContext);
        case LIST:
            if (childDataNode.isKeyedList()) {
                return new FilterKeyedListDataNode(childSchemaNode,
                        childDataNode, childLocationPath, hookRegistry,
                        operation, authContext);
            }
            // FIXME: Implement filter wrapper for unkeyed list data nodes.
            // This requires XPath expression support for indexed list elements
            // which isn't implemented yet, so we don't support filtering on
            // unkeyed lists for now.
            // return new FilterUnkeyedListDataNode();
            return childDataNode;
        case LEAF:
        case LEAF_LIST:
            // Leaf and leaf-list data nodes can't be filtered any further, so
            // there's no need to wrap them.
            return childDataNode;
        default:
            throw new InvalidSchemaTypeException();
        }
    }

    @Override
    protected Set<String> getAllChildNames() {
        return sourceDataNode.getAllChildNames();
    }

}
