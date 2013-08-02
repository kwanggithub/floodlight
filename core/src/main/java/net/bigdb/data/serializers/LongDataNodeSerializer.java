package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class LongDataNodeSerializer implements DataNodeSerializer<Long> {

    private static class InstanceHolder {
        private static final LongDataNodeSerializer INSTANCE =
                new LongDataNodeSerializer();
    }

    private LongDataNodeSerializer() {}

    public static LongDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Long l, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeNumber(l);
    }
}
