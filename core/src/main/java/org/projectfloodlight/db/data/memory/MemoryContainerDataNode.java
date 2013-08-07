package org.projectfloodlight.db.data.memory;

import java.util.Map;
import java.util.Set;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.ContainerDataNode;
import org.projectfloodlight.db.data.DataNode;

public class MemoryContainerDataNode extends MemoryDictionaryDataNode
        implements ContainerDataNode {

    public MemoryContainerDataNode(boolean mutable,
            Map<String, DataNode> initNodes) throws BigDBException {
        super(mutable, initNodes);
    }

    public MemoryContainerDataNode(MemoryContainerDataNode baseNode,
            boolean mutable, Map<String, DataNode> updates,
            Set<String> deletions) throws BigDBException {
        super(baseNode, mutable, updates, deletions);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.CONTAINER;
    }
}
