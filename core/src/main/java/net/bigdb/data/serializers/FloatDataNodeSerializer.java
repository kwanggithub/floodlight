package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class FloatDataNodeSerializer implements DataNodeSerializer<Float> {

    private static class InstanceHolder {
        private static final FloatDataNodeSerializer INSTANCE =
                new FloatDataNodeSerializer();
    }

    private FloatDataNodeSerializer() {}

    public static FloatDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Float f, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeNumber(f.doubleValue());
    }
}
