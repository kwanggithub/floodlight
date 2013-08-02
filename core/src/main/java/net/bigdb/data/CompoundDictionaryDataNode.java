package net.bigdb.data;

import java.util.HashSet;
import java.util.Set;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;
import net.bigdb.query.Step;
import net.bigdb.schema.AggregateSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.SchemaNode;

/**
 * Implementation of a dictionary data node aggregates contributions from one
 * or more underlying dictionary data nodes.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class CompoundDictionaryDataNode extends AbstractDictionaryDataNode {

    public static class Builder {

        private final SchemaNode schemaNode;
        private Set<DataNode> dataNodes = new HashSet<DataNode>();

        public Builder(SchemaNode schemaNode) {
            this.schemaNode = schemaNode;
        }

        public Builder addDataNode(DataNode dataNode) {
            dataNodes.add(dataNode);
            return this;
        }

        public CompoundDictionaryDataNode build() {
            return CompoundDictionaryDataNode.from(schemaNode, dataNodes);
        }
    }

    /** Schema node corresponding to the dictionary data node */
    private final AggregateSchemaNode schemaNode;

    /**
     * The underlying dictionary data nodes that contribute to the compound data
     * node
     */
    private final Set<DataNode> dataNodes;

    private CompoundDictionaryDataNode(SchemaNode schemaNode,
            Set<DataNode> dataNodes) {

        super();

        assert schemaNode != null;
        // A dictionary data node corresponds to either a container or a list
        // element data node, so the schema node must be either a
        // ContainerSchemaNode or a ListElementSchemaNode, both of which are
        // subclasses of AggregateSchemaNode.
        assert schemaNode instanceof AggregateSchemaNode;
        assert dataNodes != null;

        this.schemaNode = (AggregateSchemaNode) schemaNode;
        this.dataNodes = dataNodes;
    }

    /**
     * Factory method to create a new compound dictionary data node.
     *
     * @param schemaNode
     *            the schema node corresponding to the data node
     * @param dataNodes
     *            the data node contributions to the compound data node. All of
     *            the contributing data nodes should also be dictionary data
     *            nodes, i.e. isDictionary() should be true. The new dictionary
     *            data node assumes ownership of the dataNodes set. The caller
     *            should not make any modifications to the set after calling
     *            this factory method.
     * @return the newly constructed compound dictionary data node
     */
    public static CompoundDictionaryDataNode from(SchemaNode schemaNode,
            Set<DataNode> dataNodes) {
        return new CompoundDictionaryDataNode(schemaNode, dataNodes);
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
        // Computing the digest values for logical data nodes would be
        // expensive so for now we just don't support it.
        // We'll see if it's needed.
        return null;
    }

    /**
     * Look up a child data node by name. If none of the contributing data
     * nodes contain the named child then DataNode.NULL is returned. If a
     * single contributing data node contains the child then the child data node
     * returned from that contributing data node is returned directly. If
     * multiple contributing data nodes contain the child, then a new compound
     * data node is created to merge the multiple contributions.
     */
    @Override
    public DataNode getChild(Step step) throws BigDBException {

        String name = step.getName();

        // Get the schema node for the requested child. If the name argument
        // doesn't map to a child that's defined in the schema, then this will
        // throw a SchemaNodeNotFoundException.
        SchemaNode childSchemaNode = schemaNode.getChildSchemaNode(name);

        // Gather the child nodes from the contributing data nodes
        Set<DataNode> childDataNodes = new HashSet<DataNode>();
        for (DataNode dataNode: dataNodes) {
            DataNode childDataNode = dataNode.getChild(name);
            if (!childDataNode.isNull()) {
                childDataNodes.add(childDataNode);
            }
        }

        // None of the contributing data nodes contained the child, so just
        // return NULL.
        if (childDataNodes.isEmpty())
            return DataNode.NULL;

        // Only a single contributing data node contained the child, so just
        // return its child node directly
        if (childDataNodes.size() == 1)
            return childDataNodes.iterator().next();

        // Multiple contributions. Form a new compound data node to merge the
        // contributions.
        switch (childSchemaNode.getNodeType()) {
        case CONTAINER:
        case LIST_ELEMENT:
            return new CompoundDictionaryDataNode(childSchemaNode, childDataNodes);
        case LIST:
            ListSchemaNode listSchemaNode = (ListSchemaNode) childSchemaNode;
            if (listSchemaNode.getKeySpecifier() == null)
                throw new BigDBException(
                        "An unkeyed list must come from a single source");

            return CompoundKeyedListDataNode.from(childSchemaNode, childDataNodes);
        default:
            throw new BigDBException(String.format(
                    "Invalid compound data node type; type = %s; name = %s",
                    childSchemaNode.getNodeType(), name));
        }
    }

    @Override
    protected Set<String> getAllChildNames() {
        return schemaNode.getChildNodes().keySet();
    }
}
