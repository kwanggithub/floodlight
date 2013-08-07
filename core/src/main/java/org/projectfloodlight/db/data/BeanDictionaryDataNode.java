package org.projectfloodlight.db.data;

import java.util.Set;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.data.serializers.BeanDataNodeSerializer;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.AggregateSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;

/**
 * Data node implementation that wraps an object that follows the Java bean
 * conventions. Its getChild implementation uses Java bean introspection and
 * reflection (indirectly via the BeanDataNodeSerializer) to invoke the method
 * to obtain the child node value and serialize/wrap it in another data node.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public final class BeanDictionaryDataNode extends AbstractDictionaryDataNode {

    private final Object object;
    private final AggregateSchemaNode schemaNode;
    private final BeanDataNodeSerializer serializer;
    private final DataNodeFactory dataNodeFactory;

    protected BeanDictionaryDataNode(Object object, SchemaNode schemaNode,
            DataNodeFactory dataNodeFactory) throws BigDBException {
        super();
        this.object = object;
        this.schemaNode = (AggregateSchemaNode) schemaNode;
        DataNodeSerializer<?> s =
                DataNodeSerializerRegistry.getDataNodeSerializer(object
                        .getClass());
        if (!(s instanceof BeanDataNodeSerializer)) {
            throw new BigDBException("Expected bean object");
        }
        this.serializer = (BeanDataNodeSerializer) s;
        this.dataNodeFactory = dataNodeFactory;
    }

    @Override
    public DigestValue computeDigestValue() {
        // We want to lazily read/invoke the property values for this bean
        // implementation, so we don't want to force everything to be read just
        // to compute the digest value. So we don't support digest values for
        // this implementation of the DataNode interface. At least currently
        // the digest values are only used to diff data node trees which is
        // only used with config data, so it should be OK if data nodes for
        // operational state don't support digest values.
        return null;
    }

    @Override
    public DataNode.NodeType getNodeType() {
        switch (schemaNode.getNodeType()) {
        case CONTAINER:
            return DataNode.NodeType.CONTAINER;
        case LIST_ELEMENT:
            return DataNode.NodeType.LIST_ELEMENT;
        default:
            throw new BigDBInternalError(
                    "Expected container or list element node");
        }
    }

    @Override
    public DataNode getChild(Step step) throws BigDBException {
        String name = step.getName();
        SchemaNode childSchemaNode = schemaNode.getChildSchemaNode(name);
        DataNodeGenerator generator =
                new DataNodeGenerator(childSchemaNode, dataNodeFactory);
        serializer.serializeProperty(object, name, generator);
        return generator.getResult();
    }

    @Override
    protected Set<String> getAllChildNames() {
        return serializer.getAllPropertyNames();
    }
}
