package net.bigdb.data;

import java.util.Iterator;

import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.SchemaNode;

public class IterableUnkeyedListDataNode extends AbstractUnkeyedListDataNode {

    private ListSchemaNode listSchemaNode;
    private Iterable<?> iterable;

    private IterableUnkeyedListDataNode(SchemaNode listSchemaNode, Iterable<?> iterable) {

        super();

        assert listSchemaNode != null;
        assert listSchemaNode instanceof ListSchemaNode;
        assert iterable != null;

        this.listSchemaNode = (ListSchemaNode) listSchemaNode;
        this.iterable = iterable;
    }

    public static IterableUnkeyedListDataNode from(SchemaNode schemaNode,
            Iterable<?> iterable) {
        return new IterableUnkeyedListDataNode(schemaNode, iterable);
    }

    @Override
    public DigestValue computeDigestValue() {
        // Computing the digest values for logical data nodes would be
        // expensive so for now we just don't support it.
        // We'll see if it's needed.
        return null;
    }

    @Override
    public Iterator<DataNode> iterator() {
        return new DataNodeMappingIterator(iterable.iterator(),
                listSchemaNode.getListElementSchemaNode());
    }
}
