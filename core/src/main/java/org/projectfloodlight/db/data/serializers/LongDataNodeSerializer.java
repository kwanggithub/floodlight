package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

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
