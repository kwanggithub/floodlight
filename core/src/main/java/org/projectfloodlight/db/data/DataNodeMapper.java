package org.projectfloodlight.db.data;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator.Inclusion;
import org.projectfloodlight.db.data.memory.MemoryDataNodeFactory;
import org.projectfloodlight.db.schema.SchemaNode;

public class DataNodeMapper {

    private final static DataNodeMapper DEFAULT_MAPPER = new DataNodeMapper();

    private Inclusion inclusion = Inclusion.NON_EMPTY;

    public void setInclusion(Inclusion inclusion) {
        this.inclusion = inclusion;
    }

    public DataNode convertObjectToDataNode(Object object, SchemaNode schemaNode,
            DataNodeFactory dataNodeFactory) throws BigDBException {
        if (object == null)
            return dataNodeFactory.createNullDataNode();
        DataNodeGenerator generator =
                new DataNodeGenerator(schemaNode, dataNodeFactory);
        generator.setInclusion(inclusion);
        DataNodeSerializer<Object> dataNodeSerializer =
                DataNodeSerializerRegistry.getDataNodeSerializer(object.getClass());
        dataNodeSerializer.serialize(object, generator);
        return generator.getResult();
    }

    public DataNode convertObjectToDataNode(Object object, SchemaNode schemaNode)
            throws BigDBException {
        return convertObjectToDataNode(object, schemaNode,
                new MemoryDataNodeFactory());
    }

    public static DataNodeMapper getDefaultMapper() {
        return DEFAULT_MAPPER;
    }
}
