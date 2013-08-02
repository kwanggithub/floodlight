package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class StringDataNodeSerializer implements DataNodeSerializer<String> {

    private static class InstanceHolder {
        private static final StringDataNodeSerializer INSTANCE =
                new StringDataNodeSerializer();
    }

    private StringDataNodeSerializer() {}

    public static StringDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(String s, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(s);
    }
}
