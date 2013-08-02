package net.bigdb.data.memory;

import java.util.Map;
import java.util.Set;

import net.bigdb.BigDBException;
import net.bigdb.data.ContainerDataNode;
import net.bigdb.data.DataNode;

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
