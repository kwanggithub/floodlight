package net.bigdb.data;

import net.bigdb.BigDBException;
import net.bigdb.schema.SchemaNode;

/**
 * This class just subclasses DiffDictionaryDataNode to return the correct
 * data node type, i.e. CONTAINER.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class DiffContainerDataNode extends DiffDictionaryDataNode
        implements ContainerDataNode {

    public DiffContainerDataNode(SchemaNode schemaNode, DataNode oldDataNode,
            DataNode newDataNode) throws BigDBException {
        super(schemaNode, oldDataNode, newDataNode);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.CONTAINER;
    }

    @Override
    public void put(String name, DataNode dataNode) throws BigDBException {
        throw new UnsupportedOperationException("Diff data nodes are immutable");
    }

    @Override
    public DataNode remove(String name) throws BigDBException {
        throw new UnsupportedOperationException("Diff data nodes are immutable");
    }
}
