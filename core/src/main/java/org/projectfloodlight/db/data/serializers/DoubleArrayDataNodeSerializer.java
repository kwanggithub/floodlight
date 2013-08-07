package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class DoubleArrayDataNodeSerializer implements DataNodeSerializer<double[]> {

    private static class InstanceHolder {
        private static final DoubleArrayDataNodeSerializer INSTANCE =
                new DoubleArrayDataNodeSerializer();
    }

    private DoubleArrayDataNodeSerializer() {}

    public static DoubleArrayDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(double[] doubleArray, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeListStart();
        for (double d: doubleArray)
            generator.writeNumber(d);
        generator.writeListEnd();
    }
}
