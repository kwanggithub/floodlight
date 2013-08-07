package org.projectfloodlight.db.data.serializers;

import java.util.Map;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;
import org.projectfloodlight.db.data.DataNodeSerializerRegistry;

public class MapDataNodeSerializer implements
        DataNodeSerializer<Map<?, ?>> {

    private static class InstanceHolder {
        private static final MapDataNodeSerializer INSTANCE =
                new MapDataNodeSerializer();
    }

    private MapDataNodeSerializer() {
    }

    public static MapDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Map<?, ?> map, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeMapStart();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            generator.writeFieldStart(entry.getKey().toString(), false);
            Object object = entry.getValue();
            if (object == null) {
                generator.writeNull();
            } else {
                Class<?> elementClass = object.getClass();
                DataNodeSerializer<Object> dataNodeSerializer =
                        DataNodeSerializerRegistry
                                .getDataNodeSerializer(elementClass);
                dataNodeSerializer.serialize(object, generator);
            }
        }
        generator.writeMapEnd();
    }
}
