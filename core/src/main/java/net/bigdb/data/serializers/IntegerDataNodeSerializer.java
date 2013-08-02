package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

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
