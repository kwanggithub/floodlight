package org.projectfloodlight.db.data.memory;

import java.util.Map;
import java.util.Set;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.ListElementDataNode;

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
