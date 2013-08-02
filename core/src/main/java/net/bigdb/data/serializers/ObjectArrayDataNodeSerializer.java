package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeSerializerRegistry;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class ObjectArrayDataNodeSerializer implements
        DataNodeSerializer<Object[]> {

    private static class InstanceHolder {
        private static final ObjectArrayDataNodeSerializer INSTANCE =
                new ObjectArrayDataNodeSerializer();
    }

    private ObjectArrayDataNodeSerializer() {
    }

    public static ObjectArrayDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Object[] objectArray, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeListStart();
        for (Object object : objectArray) {
            DataNodeSerializerRegistry.serializeObject(object, generator);
        }
        generator.writeListEnd();
    }
}
