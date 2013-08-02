package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class FloatArrayDataNodeSerializer implements DataNodeSerializer<float[]> {

    private static class InstanceHolder {
        private static final FloatArrayDataNodeSerializer INSTANCE =
                new FloatArrayDataNodeSerializer();
    }

    private FloatArrayDataNodeSerializer() {}

    public static FloatArrayDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(float[] floatArray, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeListStart();
        for (float f: floatArray)
            generator.writeNumber((double)f);
        generator.writeListEnd();
    }
}
