package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

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
