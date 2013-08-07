package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class IntegerDataNodeSerializer implements DataNodeSerializer<Integer> {

    private static class InstanceHolder {
        private static final IntegerDataNodeSerializer INSTANCE =
                new IntegerDataNodeSerializer();
    }

    private IntegerDataNodeSerializer() {}

    public static IntegerDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Integer i, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeNumber(i);
    }
}
