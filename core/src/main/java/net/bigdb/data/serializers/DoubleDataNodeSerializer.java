package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class DoubleDataNodeSerializer implements DataNodeSerializer<Double> {

    private static class InstanceHolder {
        private static final DoubleDataNodeSerializer INSTANCE =
                new DoubleDataNodeSerializer();
    }

    private DoubleDataNodeSerializer() {}

    public static DoubleDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Double d, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeNumber(d);
    }
}
