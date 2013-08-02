package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class ShortDataNodeSerializer implements DataNodeSerializer<Short> {

    private static class InstanceHolder {
        private static final ShortDataNodeSerializer INSTANCE =
                new ShortDataNodeSerializer();
    }

    private ShortDataNodeSerializer() {}

    public static ShortDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Short s, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeNumber(s);
    }
}
