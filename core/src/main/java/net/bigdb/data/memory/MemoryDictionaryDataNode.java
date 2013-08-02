package net.bigdb.data.memory;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.annotation.Nonnull;

import net.bigdb.BigDBException;
import net.bigdb.data.AbstractDictionaryDataNode;
import net.bigdb.data.ContainerDataNode;
import net.bigdb.data.DataNode;
import net.bigdb.query.Step;
import edu.stanford.ppl.concurrent.SnapTreeMap;

public abstract class MemoryDictionaryDataNode extends AbstractDictionaryDataNode implements ContainerDataNode {

    protected final SnapTreeMap<String, DataNode> childNodes;

    public MemoryDictionaryDataNode(boolean mutable,
            Map<String, DataNode> initNodes) throws BigDBException {
        super();
        if (initNodes != null)
            this.childNodes = new SnapTreeMap<String, DataNode>(initNodes);
        else
            this.childNodes = new SnapTreeMap<String, DataNode>();
        if (!mutable)
            freeze();
    }

    public MemoryDictionaryDataNode(MemoryDictionaryDataNode baseNode,
            boolean mutable, Map<String, DataNode> updates,
            Set<String> deletions) throws BigDBException {
        super();
        this.childNodes = baseNode.childNodes.clone();
        if (updates != null) {
            this.childNodes.putAll(updates);
        }
        if (deletions != null) {
            for (String key: deletions)
                this.childNodes.remove(key);
        }
        if (!mutable)
            freeze();
    }

    @Override
    public int childCount() {
        return childNodes.size();
    }

    @Override
    public boolean hasChildren() {
        return !childNodes.isEmpty();
    }

    @Override
    public boolean hasChild(String name) throws BigDBException {
        return childNodes.containsKey(name);
    }

    @Override
    public Iterator<DataNode> iterator() {
        return childNodes.values().iterator();
    }

    @Override
    public SortedSet<String> getChildNames() {
        return Collections.unmodifiableSortedSet(childNodes.keySet());
    }

    @Override
    public Set<String> getAllChildNames() {
        return Collections.unmodifiableSortedSet(childNodes.keySet());
    }

    @Override
    @Nonnull
    public DataNode getChild(Step step) throws BigDBException {
        DataNode child = childNodes.get(step.getName());
        return (child != null) ? child : DataNode.NULL;
    }

    @Override
    public void put(String name, DataNode dataNode) throws BigDBException {
        checkMutable();
        childNodes.put(name, dataNode);
    }

    @Override
    public DataNode remove(String name) throws BigDBException {
        checkMutable();
        DataNode dataNode = childNodes.remove(name);
        return dataNode;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result +
                        ((childNodes == null) ? 0 : childNodes.hashCode());
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
        MemoryDictionaryDataNode other = (MemoryDictionaryDataNode) obj;
        if (childNodes == null) {
            if (other.childNodes != null)
                return false;
        } else if (!childNodes.equals(other.childNodes))
            return false;
        return true;
    }
}
