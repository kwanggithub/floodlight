package net.bigdb.data.memory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bigdb.BigDBException;
import net.bigdb.data.AbstractKeyedListDataNode;
import net.bigdb.data.DataNode;
import net.bigdb.data.IndexSpecifier;
import net.bigdb.data.IndexValue;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import edu.stanford.ppl.concurrent.SnapTreeMap;

public class MemoryKeyedListDataNode extends AbstractKeyedListDataNode {

    private class KeyedListEntryIterator extends UnmodifiableIterator<KeyedListEntry> {

        private Iterator<Map.Entry<IndexValue, DataNode>> mapEntryIterator;

        KeyedListEntryIterator() {
            this.mapEntryIterator = keyedElements.entrySet().iterator();
        }

        @Override
        public boolean hasNext() {
            return mapEntryIterator.hasNext();
        }

        @Override
        public KeyedListEntry next() {
            Map.Entry<IndexValue, DataNode> mapEntry = mapEntryIterator.next();
            return new KeyedListEntryImpl(mapEntry.getKey(), mapEntry.getValue());
        }
    }

    private class KeyedListEntryIterable implements Iterable<KeyedListEntry> {
        @Override
        public Iterator<KeyedListEntry> iterator() {
            return new KeyedListEntryIterator();
        }
    }

    private final IndexSpecifier keySpecifier;
    private final SnapTreeMap<IndexValue, DataNode> keyedElements;

    public MemoryKeyedListDataNode(IndexSpecifier keySpecifier) throws BigDBException {
        this(true, keySpecifier, null);
    }

    public MemoryKeyedListDataNode(boolean mutable, IndexSpecifier keySpecifier, Iterator<DataNode> elements)
            throws BigDBException {
        super();
        assert keySpecifier != null;

        this.keySpecifier = keySpecifier;
        this.keyedElements = new SnapTreeMap<IndexValue, DataNode>();

        if (elements != null)
            addAll(elements);

        if (!mutable)
            this.freeze();
    }

    public MemoryKeyedListDataNode(MemoryKeyedListDataNode baseNode,
            List<DataNode> updates, Set<IndexValue> deletions)
            throws BigDBException {
        super();

        keySpecifier = baseNode.keySpecifier;

        assert keySpecifier != null;

        keyedElements = baseNode.keyedElements.clone();

        if (deletions != null) {
            for (IndexValue keyValue : deletions) {
                this.remove(keyValue);
            }
        }

        addAll(updates);

        this.freeze();
    }

    /**
     * Constructor that specifies the list element map directly. This lets us
     * construct a tree with NULL list element data nodes (but still valid index
     * values) to represent deleted list elements. The resulting data node is
     * immutable.
     *
     * @param keySpecifier
     *            The key specified for the list. Cannot be null.
     * @param listElements
     *            The list elements included in the new list. DataNode values in
     *            the list element entries can be NULL to indicate a deleted
     *            list element.
     * @throws BigDBException
     */
    public MemoryKeyedListDataNode(IndexSpecifier keySpecifier,
            Map<IndexValue, DataNode> listElements) throws BigDBException {
        super();
        assert keySpecifier != null;
        assert listElements != null;
        this.keySpecifier = keySpecifier;
        this.keyedElements = new SnapTreeMap<IndexValue, DataNode>(listElements);
        this.freeze();
    }

    @Override
    public IndexSpecifier getKeySpecifier() {
        return keySpecifier;
    }

    @Override
    public int childCount() throws BigDBException {
        return keyedElements.size();
    }

    @Override
    public boolean hasChildren() throws BigDBException {
        return !keyedElements.isEmpty();
    }

    @Override
    public Iterable<KeyedListEntry> getKeyedListEntries() {
        return new KeyedListEntryIterable();
    }

    @Override
    public boolean hasChild(IndexValue indexValue) throws BigDBException {
        return keyedElements.containsKey(indexValue);
    }

    @Override
    public DataNode getChild(IndexValue indexValue) throws BigDBException {
        DataNode res = keyedElements.get(indexValue);
        return res != null ? res : DataNode.NULL;
    }

    @Override
    public void add(DataNode dataNode) throws BigDBException {
        checkMutable();
        IndexValue keyValue = IndexValue.fromListElement(keySpecifier, dataNode);
        keyedElements.put(keyValue, dataNode);
    }

    @Override
    public DataNode remove(IndexValue indexValue) throws BigDBException {
        checkMutable();
        DataNode dataNode = keyedElements.remove(indexValue);
        return dataNode;
    }

    @Override
    public Iterator<DataNode> iterator() {
        return Iterators.unmodifiableIterator(keyedElements.values().iterator());
    }

    @Override
    public void add(IndexValue indexValue, DataNode dataNode)
            throws BigDBException {
        keyedElements.put(indexValue, dataNode);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result +
                        ((keySpecifier == null) ? 0 : keySpecifier.hashCode());
        result =
                prime *
                        result +
                        ((keyedElements == null) ? 0 : keyedElements.hashCode());
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
        MemoryKeyedListDataNode other = (MemoryKeyedListDataNode) obj;
        if (keySpecifier == null) {
            if (other.keySpecifier != null)
                return false;
        } else if (!keySpecifier.equals(other.keySpecifier))
            return false;
        if (keyedElements == null) {
            if (other.keyedElements != null)
                return false;
        } else if (!keyedElements.equals(other.keyedElements))
            return false;
        return true;
    }
}
