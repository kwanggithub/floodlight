package net.bigdb.data.memory;

import java.util.Map;
import java.util.Set;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.data.ListElementDataNode;

public class MemoryListElementDataNode extends MemoryDictionaryDataNode
        implements ListElementDataNode {

    public MemoryListElementDataNode(boolean mutable,
            Map<String, DataNode> initNodes) throws BigDBException {
        super(mutable, initNodes);
    }

    public MemoryListElementDataNode(MemoryListElementDataNode baseNode,
            boolean mutable, Map<String, DataNode> updates,
            Set<String> deletions) throws BigDBException  {
        super(baseNode, mutable, updates, deletions);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.LIST_ELEMENT;
    }
}
