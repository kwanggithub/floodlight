package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class ShortArrayDataNodeSerializer implements DataNodeSerializer<short[]> {

    private static class InstanceHolder {
        private static final ShortArrayDataNodeSerializer INSTANCE =
                new ShortArrayDataNodeSerializer();
    }

    private ShortArrayDataNodeSerializer() {}

    public static ShortArrayDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(short[] shortArray, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeListStart();
        for (short s: shortArray)
            generator.writeNumber(s);
        generator.writeListEnd();
    }
}
