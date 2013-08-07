package org.projectfloodlight.db.data;

import java.util.Iterator;

import org.projectfloodlight.db.schema.LeafListSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;

public class IterableLeafListDataNode extends AbstractLeafListDataNode {

    private LeafListSchemaNode leafListSchemaNode;
    private Iterable<?> iterable;

    private IterableLeafListDataNode(SchemaNode leafListSchemaNode,
            Iterable<?> iterable) {

        super();

        assert leafListSchemaNode != null;
        assert leafListSchemaNode instanceof LeafListSchemaNode;
        assert iterable != null;

        this.leafListSchemaNode = (LeafListSchemaNode) leafListSchemaNode;
        this.iterable = iterable;
    }

    public static IterableLeafListDataNode from(SchemaNode leafListSchemaNode,
            Iterable<?> iterable) {
        return new IterableLeafListDataNode(leafListSchemaNode, iterable);
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
                leafListSchemaNode.getLeafSchemaNode());
    }
}
