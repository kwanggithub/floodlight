package org.projectfloodlight.db.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.AggregateSchemaNode;
import org.projectfloodlight.db.schema.InvalidSchemaTypeException;
import org.projectfloodlight.db.schema.ListElementSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;

/**
 * A dictionary data node implementation that applies the selected nodes that
 * were specified in the "select" params of the query to filter the child nodes
 * that are visible in the data node
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class SelectDictionaryDataNode extends AbstractDictionaryDataNode {

    /**
     * The schema node corresponding to this data node. This should be either a
     * ContainerSchemaNode or ListElementSchemaNode, both of which are
     * subclasses of AggregateSchemaNode.
     */
    private final AggregateSchemaNode schemaNode;

    /**
     * The underlying data node that's being filtered for the selected paths
     * FIXME: Ideally this would just be a regular data node and not have to be
     * an AbstractDictionaryDataNode.
     */
    private final AbstractDictionaryDataNode sourceDataNode;

    /**
     * The selected paths that are visible to callers of this data node.
     * The selected path starts with a step representing this node itself.
     * This is redundant for dictionary nodes, but is necessary for list nodes
     * and to make the conventions for the selected paths consistent across
     * all of the nodes we always include the initial step for the node itself.
     */
    private final Collection<LocationPathExpression> selectedPaths =
            new ArrayList<LocationPathExpression>();

    public SelectDictionaryDataNode(SchemaNode schemaNode,
            DataNode sourceDataNode,
            Collection<LocationPathExpression> selectedPaths) {

        super();

        assert schemaNode != null;
        assert schemaNode instanceof AggregateSchemaNode;
        assert sourceDataNode != null;
        assert sourceDataNode instanceof AbstractDictionaryDataNode;
        assert selectedPaths != null;

        this.schemaNode = (AggregateSchemaNode) schemaNode;
        this.sourceDataNode = (AbstractDictionaryDataNode) sourceDataNode;
        this.selectedPaths.addAll(selectedPaths);
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

        if (schemaNode.getNodeType() == SchemaNode.NodeType.LIST_ELEMENT) {
            ListElementSchemaNode listElementSchemaNode =
                    (ListElementSchemaNode) schemaNode;
            if (listElementSchemaNode.getKeyNodeNames().contains(name)) {
                return sourceDataNode.getChild(step);
            }
        }

        Collection<LocationPathExpression> childSelectedPaths =
                new ArrayList<LocationPathExpression>();
        for (LocationPathExpression selectedPath: selectedPaths) {
            // FIXME: From code review:
            // Could this special case be checked on construction i.e., if you
            // have a path that is size()==1 you don't actually construct a
            // SelectDictionaryDatanode?
            // e.g., via a function
            // public static DataNode select(DataNode dataNode, ....) {
            //      return a SelectDataNode if necessary, else just the DataNode
            // }
            if (selectedPath.size() == 1) {
                return sourceDataNode.getChild(step);
            }
            String selectedName = selectedPath.getStep(1).getName();
            if (selectedName.equals(name)) {
                childSelectedPaths.add(selectedPath.subpath(1));
            }
        }

        if (childSelectedPaths.isEmpty())
            return DataNode.NULL;

        Step selectedStep = childSelectedPaths.iterator().next().getStep(0);

        // Look up the child data node. If the child doesn't exists then we
        // don't need to do any filtering/wrapping of the data node
        DataNode childDataNode = sourceDataNode.getChild(selectedStep);
        if (childDataNode.isNull())
            return childDataNode;

        // Get the schema node for the requested child. If the name argument
        // doesn't map to a child that's defined in the schema, then this will
        // throw a SchemaNodeNotFoundException.
        SchemaNode childSchemaNode = schemaNode.getChildSchemaNode(name);

        // If we reach here, then the child node wasn't filtered. Aggregate
        // child data nodes need to be wrapped in another filter data node.
        switch (childSchemaNode.getNodeType()) {
        case CONTAINER:
        case LIST_ELEMENT:
            return new SelectDictionaryDataNode(childSchemaNode, childDataNode,
                    childSelectedPaths);
        case LIST:
            if (childDataNode.isKeyedList()) {
                return new SelectKeyedListDataNode(childSchemaNode,
                        childDataNode, childSelectedPaths);
            }
            // FIXME: Implement select wrapper for unkeyed list data nodes.
            // This requires XPath expression support for indexed list elements
            // which isn't implemented yet, so we don't support filtering on
            // unkeyed lists for now.
            //return new SelectUnkeyedListDataNode(childSchemaNode,
            //        childDataNode, childSelectedPaths);
            //return childDataNode;
            //throw new UnsupportedOperationException(
            //        "Selecting on unkeyed lists is not supported yet");
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
        Set<String> selectedChildNames = new TreeSet<String>();

        // For list elements in keyed lists implicitly include the key names
        if (schemaNode.getNodeType() == SchemaNode.NodeType.LIST_ELEMENT) {
            ListElementSchemaNode listElementSchemaNode =
                    (ListElementSchemaNode) schemaNode;
            selectedChildNames.addAll(listElementSchemaNode.getKeyNodeNames());
        }

        for (LocationPathExpression selectedPath: selectedPaths) {
            if (selectedPath.size() == 1)
                return sourceDataNode.getAllChildNames();
            Step step = selectedPath.getStep(1);
            String name = step.getName();
            selectedChildNames.add(name);
        }
        return selectedChildNames;
    }
}
