package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

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
