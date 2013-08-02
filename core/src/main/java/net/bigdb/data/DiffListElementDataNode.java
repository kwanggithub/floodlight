package net.bigdb.data;

import net.bigdb.BigDBException;
import net.bigdb.schema.SchemaNode;

/**
 * This class just subclasses DiffDictionaryDataNode to return the correct
 * data node type, i.e. LIST_ELEMENT.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class DiffListElementDataNode extends DiffDictionaryDataNode
        implements ListElementDataNode {

    public DiffListElementDataNode(SchemaNode schemaNode, DataNode oldDataNode,
            DataNode newDataNode) throws BigDBException {
        super(schemaNode, oldDataNode, newDataNode);
        // TODO Auto-generated constructor stub
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.LIST_ELEMENT;
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
