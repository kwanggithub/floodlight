package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

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
