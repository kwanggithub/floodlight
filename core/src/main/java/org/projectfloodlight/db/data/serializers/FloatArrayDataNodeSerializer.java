package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

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
