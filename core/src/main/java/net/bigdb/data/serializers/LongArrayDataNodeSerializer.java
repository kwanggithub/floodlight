package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class LongArrayDataNodeSerializer implements DataNodeSerializer<long[]> {

    private static class InstanceHolder {
        private static final LongArrayDataNodeSerializer INSTANCE =
                new LongArrayDataNodeSerializer();
    }

    private LongArrayDataNodeSerializer() {}

    public static LongArrayDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(long[] longArray, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeListStart();
        for (long l: longArray)
            generator.writeNumber(l);
        generator.writeListEnd();
    }
}
