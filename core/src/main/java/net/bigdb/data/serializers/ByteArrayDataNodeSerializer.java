package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class ByteArrayDataNodeSerializer implements DataNodeSerializer<byte[]> {

    private static class InstanceHolder {
        private static final ByteArrayDataNodeSerializer INSTANCE =
                new ByteArrayDataNodeSerializer();
    }

    private ByteArrayDataNodeSerializer() {}

    public static ByteArrayDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(byte[] byteArray, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeListStart();
        for (byte b: byteArray)
            generator.writeNumber(b);
        generator.writeListEnd();
    }
}
