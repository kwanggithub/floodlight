package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class ByteDataNodeSerializer implements DataNodeSerializer<Byte> {

    private static class InstanceHolder {
        private static final ByteDataNodeSerializer INSTANCE =
                new ByteDataNodeSerializer();
    }

    private ByteDataNodeSerializer() {}

    public static ByteDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Byte b, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeNumber(b.longValue());
    }
}
