package org.projectfloodlight.db.data.memory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.AbstractUnkeyedListDataNode;
import org.projectfloodlight.db.data.DataNode;

import com.google.common.collect.Iterators;

public class MemoryUnkeyedListDataNode extends AbstractUnkeyedListDataNode {

    protected final List<DataNode> elements;

    public MemoryUnkeyedListDataNode() throws BigDBException {
        this(true, null);
    }

    public MemoryUnkeyedListDataNode(boolean mutable,
            Iterator<DataNode> elements) throws BigDBException {
        super();
        this.elements = new ArrayList<DataNode>();

        if (elements != null)
            addAll(elements);

        if (!mutable)
            this.freeze();
    }

    public MemoryUnkeyedListDataNode(MemoryUnkeyedListDataNode baseNode,
            List<DataNode> updates)
            throws BigDBException {
        super();
        elements = new ArrayList<DataNode>();
        addAll(baseNode.elements);
        addAll(updates);
        this.freeze();
    }


    @Override
    public int childCount() throws BigDBException {
        return elements.size();
    }

    @Override
    public boolean hasChildren() throws BigDBException {
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
        return Iterators.unmodifiableIterator(elements.iterator());
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
        MemoryUnkeyedListDataNode other = (MemoryUnkeyedListDataNode) obj;
        if (elements == null) {
            if (other.elements != null)
                return false;
        } else if (!elements.equals(other.elements))
            return false;
        return true;
    }
}
