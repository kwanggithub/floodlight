package net.bigdb.data.memory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.data.AbstractLeafListDataNode;
import net.bigdb.data.DataNode;

public class MemoryLeafListDataNode extends AbstractLeafListDataNode {
    private final List<DataNode> elements;

    public MemoryLeafListDataNode() throws BigDBException {
        this(true, null);
    }

    public MemoryLeafListDataNode(boolean mutable,
            Collection<DataNode> elements) throws BigDBException {
        super();
        if (elements != null)
            this.elements = new ArrayList<DataNode>(elements);
        else
            this.elements = new ArrayList<DataNode>();
        if (!mutable)
            freeze();
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.LEAF_LIST;
    }

    @Override
    public int childCount() throws BigDBException {
        return elements.size();
    }

    @Override
    public boolean hasChildren() {
        return !elements.isEmpty();
    }

    @Override
    public void add(DataNode dataNode) throws BigDBException {
        checkMutable();
        elements.add(dataNode);
    }

    @Override
    public void add(int index, DataNode dataNode) throws BigDBException {
        checkMutable();
        elements.add(index, dataNode);
    }

    @Override
    public DataNode remove(int index) throws BigDBException {
        checkMutable();
        DataNode dataNode = elements.remove(index);
        return dataNode;
    }

    @Override
    public DataNode getChild(int index) {
        return elements.get(index);
    }

    @Override
    public Iterator<DataNode> iterator() {
        return elements.iterator();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result + ((elements == null) ? 0 : elements.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MemoryLeafListDataNode other = (MemoryLeafListDataNode) obj;
        if (elements == null) {
            if (other.elements != null)
                return false;
        } else if (!elements.equals(other.elements))
            return false;
        return true;
    }
}
