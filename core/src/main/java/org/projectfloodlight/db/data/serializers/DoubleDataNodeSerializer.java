package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

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
