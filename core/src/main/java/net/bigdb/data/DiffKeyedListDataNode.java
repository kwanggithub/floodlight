package net.bigdb.data;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.SchemaNode;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

@JsonSerialize(using=DiffKeyedListDataNode.Serializer.class)
public class DiffKeyedListDataNode extends AbstractKeyedListDataNode {

    private static class EntryIteratorInfo {

        Iterator<KeyedListEntry> entryIterator;
        IndexValue pendingKeyValue;

        EntryIteratorInfo(Iterator<KeyedListEntry> entryIterator) {
            this.entryIterator = entryIterator;
            advancePendingKeyValue();
        }

        void advancePendingKeyValue() {
            pendingKeyValue = entryIterator.hasNext() ?
                    entryIterator.next().getKeyValue() : null;
        }
    }

    private class DiffKeyedListEntryIterator extends UnmodifiableIterator<KeyedListEntry> {

        private EntryIteratorInfo oldIteratorInfo;
        private EntryIteratorInfo newIteratorInfo;
        private KeyedListEntry pendingEntry;

        DiffKeyedListEntryIterator() {
            try {
                // Initialize the iterators over the dictionary entries for the
                // underlying old and new data nodes.
                oldIteratorInfo = new EntryIteratorInfo(
                        oldDataNode.getKeyedListEntries().iterator());
                Iterator<KeyedListEntry> newEntryIterator = newDataNode.isNull()
                        ? Iterators.<KeyedListEntry>emptyIterator()
                        : newDataNode.getKeyedListEntries().iterator();
                newIteratorInfo = new EntryIteratorInfo(newEntryIterator);
                updatePendingEntry();
            } catch (BigDBException e) {
                throw new UnsupportedOperationException(
                        "Unexpected error building diff keyed list entry iterator", e);
            }
        }

        /**
         * Find the next list element that's different.
         * @throws BigDBException
         */
        void updatePendingEntry() {
            // Reset to null in case we don't find anything
            pendingEntry = null;

            // Iterate until there's no more elements in either the old or
            // new data nodes.
            while ((oldIteratorInfo.pendingKeyValue != null) ||
                    (newIteratorInfo.pendingKeyValue != null)) {
                // Figure out which (or both) of the old/new data nodes has
                // the next list element
                int compare = 0;
                if (oldIteratorInfo.pendingKeyValue != null) {
                    if (newIteratorInfo.pendingKeyValue != null) {
                        // Both old and new data nodes have more list elements
                        // Compare to see which key value is the smallest
                        compare = oldIteratorInfo.pendingKeyValue.compareTo(
                                newIteratorInfo.pendingKeyValue);
                    } else {
                        // No more old list elements
                        compare = -1;
                    }
                } else {
                    // No more new list elements
                    compare = 1;
                }

                // Get the next key value and advance the iterator(s)
                IndexValue keyValue;
                if (compare < 0) {
                    keyValue = oldIteratorInfo.pendingKeyValue;
                    oldIteratorInfo.advancePendingKeyValue();
                } else if (compare > 0) {
                    keyValue = newIteratorInfo.pendingKeyValue;
                    newIteratorInfo.advancePendingKeyValue();
                } else {
                    keyValue = oldIteratorInfo.pendingKeyValue;
                    oldIteratorInfo.advancePendingKeyValue();
                    newIteratorInfo.advancePendingKeyValue();
                }

                try {
                    // Let getChild do most of the work
                    DataNode dataNode = getChild(keyValue);
                    if (!dataNode.isNull() || (dataNode == DataNode.DELETED)) {
                        pendingEntry = new KeyedListEntryImpl(keyValue, dataNode);
                        break;
                    }
                }
                catch (BigDBException e) {
                    // This error should never happen, since we're calling
                    // getChild with the values return from getChildNames,
                    // so we should never get a SchemaNodeNotFoundException.
                    throw new BigDBInternalError(String.format(
                            "Error advancing diff keyed list entry iterator " +
                            "at list element \"%s\" for schema node \"%s\".",
                            keyValue.toString(), schemaNode.toString()), e);
                }
            }
        }

        @Override
        public boolean hasNext() {
            return pendingEntry != null;
        }

        @Override
        public KeyedListEntry next() {
            if (pendingEntry == null)
                throw new NoSuchElementException();
            KeyedListEntry returnEntry = pendingEntry;
            updatePendingEntry();
            return returnEntry;
        }
    }

    /**
     * Implementation of the Iterable interface that just returns the above
     * dictionary entry iterator.
     */
    private class DiffKeyedListEntryIterable implements Iterable<KeyedListEntry> {
        @Override
        public Iterator<KeyedListEntry> iterator() {
            return new DiffKeyedListEntryIterator();
        }
    }

    /**
     * To iterate over just the list element data nodes we leverage the above
     * dictionary entry iterator logic and just discard the key value that's
     * included in each dictionary entry.
     */
    private class DiffIterator extends UnmodifiableIterator<DataNode> {

        Iterator<KeyedListEntry> keyedListEntryIterator;

        DiffIterator() {
            keyedListEntryIterator = new DiffKeyedListEntryIterator();
        }

        @Override
        public boolean hasNext() {
            return keyedListEntryIterator.hasNext();
        }

        @Override
        public DataNode next() {
            KeyedListEntry entry = keyedListEntryIterator.next();
            return entry.getDataNode();
        }
    }

    /** The schema node corresponding to this diff data node. This should
     * be either a ContainerSchemaNode or ListElementSchemaNode.
     */
    private ListSchemaNode schemaNode;

    /** The old data node from which to compute the differences */
    private DataNode oldDataNode;

    /**
     * The new data node from which to compute the differences.
     * This may be DataNode.NULL if the entire list was deleted in the new tree.
     */
    private DataNode newDataNode;

    public DiffKeyedListDataNode(SchemaNode schemaNode, DataNode oldDataNode,
            DataNode newDataNode) throws BigDBException {
        super();

        assert schemaNode != null;
        assert oldDataNode != null;

        this.schemaNode = (ListSchemaNode) schemaNode;
        this.oldDataNode = oldDataNode;
        this.newDataNode = newDataNode;

        freeze();
    }

    @Override
    public DigestValue computeDigestValue() {
        // Computing the digest values for diff data nodes could be
        // expensive so for now we just don't support it.
        // We'll see if it's needed.
        return null;
    }

    @Override
    public boolean isKeyedList() {
        return true;
    }

    @Override
    public int childCount() throws BigDBException {
        // FIXME: This is pretty inefficient because we incur the overhead of
        // iterating over and computing all of the diff entries, but it's going
        // to be inefficient anyway, so hopefully clients won't use this often.
        int count = 0;
        Iterator<KeyedListEntry> iter = new DiffKeyedListEntryIterator();
        while (iter.hasNext()) {
            count++;
            iter.next();
        }
        return count;
    }

    @Override
    public boolean hasChildren() throws BigDBException {
        Iterator<KeyedListEntry> iter = new DiffKeyedListEntryIterator();
        return iter.hasNext();
    }

    @Override
    public Iterator<DataNode> iterator() {
        return new DiffIterator();
    }

    @Override
    public IndexSpecifier getKeySpecifier() {
        return schemaNode.getKeySpecifier();
    }

    @Override
    public Iterable<KeyedListEntry> getKeyedListEntries() {
        return new DiffKeyedListEntryIterable();
    }

    @Override
    public boolean hasChild(IndexValue keyValue) throws BigDBException {
        DataNode dataNode = getChild(keyValue);
        return !dataNode.isNull() || (dataNode == DataNode.DELETED);
    }

    @Override
    public DataNode getChild(IndexValue keyValue) throws BigDBException {
        // By default return no diff
        DataNode diffDataNode = DataNode.NULL;

        // Get both the old and new list elements
        DataNode oldListElementDataNode = oldDataNode.getChild(keyValue);
        DataNode newListElementDataNode = newDataNode.getChild(keyValue);

        // Compare the list elements
        if (oldListElementDataNode.isNull()) {
            if (!newListElementDataNode.isNull()) {
                // List element was added in the new tree
                diffDataNode = newListElementDataNode;
            }
        } else if (newListElementDataNode.isNull()) {
            // List element was deleted in the new tree
            diffDataNode = DataNode.DELETED;
        } else {
            // List element exists in both the old and new trees.
            // See if they're different
            DigestValue oldDigestValue = oldListElementDataNode.getDigestValue();
            assert oldDigestValue != null;
            DigestValue newDigestValue = newListElementDataNode.getDigestValue();
            assert newDigestValue != null;
            if (!oldDigestValue.equals(newDigestValue)) {
                diffDataNode = new DiffListElementDataNode(
                        schemaNode.getListElementSchemaNode(),
                        oldListElementDataNode, newListElementDataNode);
            }
        }

        return diffDataNode;
    }

    /**
     * Override the JSON serialization for the list diff so that we can
     * represent deleted list elements in the output. With the normal JSON
     * serialization (i.e. array of dictionary-style list elements) there's no
     * way to represent the key value of a deleted list element. With this
     * serializer we instead use an object/dictionary-style JSON syntax to
     * represent the keyed list. The key string for the list element is the
     * toString output of the IndexValue. Deleted list elements are represented
     * by a null value.
     */
    static class Serializer extends JsonSerializer<DataNode> {

        @Override
        public void serialize(DataNode dataNode,
                JsonGenerator generator, SerializerProvider provider)
                throws IOException, JsonProcessingException {
            try {
                generator.writeStartObject();
                for (KeyedListEntry entry: dataNode.getKeyedListEntries()) {
                    IndexValue keyValue = entry.getKeyValue();
                    DataNode childNode = entry.getDataNode();
                    generator.writeObjectField(keyValue.toString(), childNode);
                }
                generator.writeEndObject();
            }
            catch (BigDBException e) {
                assert false;
            }
        }
    }
}
