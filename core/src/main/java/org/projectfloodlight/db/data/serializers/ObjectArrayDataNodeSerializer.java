package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;
import org.projectfloodlight.db.data.DataNodeSerializerRegistry;

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
