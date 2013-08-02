package net.bigdb.data;

import java.util.Iterator;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.SchemaNode;

import com.google.common.collect.UnmodifiableIterator;

public class IterableKeyedListDataNode extends AbstractKeyedListDataNode {

    /**
     * Implementation of the entry iterator that converts the result from
     * the application iterator to a data node.
     */
    private class IterableKeyedListEntryIterator extends UnmodifiableIterator<KeyedListEntry> {

        /** The iterator from the application code */
        private Iterator<?> iterator;

        IterableKeyedListEntryIterator() {
            iterator = iterable.iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public KeyedListEntry next() {
            try {
                // Convert the object that's returned from the
                // application-defined iterator to a data node.
                Object object = iterator.next();
                DataNodeMapper mapper = DataNodeMapper.getDefaultMapper();
                DataNode listElementDataNode =
                        mapper.convertObjectToDataNode(object, listSchemaNode
                                .getListElementSchemaNode());

                // Wrap it in a KeyedListEntry object
                IndexValue keyValue =
                        DataNodeUtilities.getKeyValue(listSchemaNode
                                .getKeySpecifier(), listElementDataNode);
                KeyedListEntry keyedListEntry =
                        new KeyedListEntryImpl(keyValue, listElementDataNode);
                return keyedListEntry;
            } catch (BigDBException e) {
                throw new BigDBInternalError(
                        "Unexpected error converting application object to data node");
            }
        }
    }

    private class IterableKeyedListEntryIterable implements Iterable<KeyedListEntry> {
        @Override
        public Iterator<KeyedListEntry> iterator() {
            return new IterableKeyedListEntryIterator();
        }
    }

    private ListSchemaNode listSchemaNode;
    private Iterable<?> iterable;

    private IterableKeyedListDataNode(SchemaNode listSchemaNode, Iterable<?> iterable) {

        super();

        assert listSchemaNode != null;
        assert listSchemaNode instanceof ListSchemaNode;
        assert iterable != null;

        this.listSchemaNode = (ListSchemaNode) listSchemaNode;
        this.iterable = iterable;
    }

    public static IterableKeyedListDataNode from(SchemaNode schemaNode,
            Iterable<?> iterable) {
        return new IterableKeyedListDataNode(schemaNode, iterable);
    }

    @Override
    public DigestValue computeDigestValue() {
        // Computing the digest values for logical data nodes would be
        // expensive so for now we just don't support it.
        // We'll see if it's needed.
        return null;
    }

    @Override
    public Iterable<KeyedListEntry> getKeyedListEntries() {
        return new IterableKeyedListEntryIterable();
    }

    @Override
    public IndexSpecifier getKeySpecifier() {
        return listSchemaNode.getKeySpecifier();
    }
}
