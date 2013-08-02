package net.bigdb.data;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator.Inclusion;
import net.bigdb.data.memory.MemoryDataNodeFactory;
import net.bigdb.schema.SchemaNode;

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
