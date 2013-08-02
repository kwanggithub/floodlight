package net.bigdb.data.serializers;

import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeSerializerRegistry;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

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
