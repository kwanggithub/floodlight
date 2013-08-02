package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class ToStringDataNodeSerializer implements DataNodeSerializer<Object> {

    private static class InstanceHolder {
        private static final ToStringDataNodeSerializer INSTANCE =
                new ToStringDataNodeSerializer();
    }

    private ToStringDataNodeSerializer() {}

    public static ToStringDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Object object, DataNodeGenerator generator)
            throws BigDBException {
        if (object == null) {
            generator.writeNull();
        } else {
            generator.writeString(object.toString());
        }
    }
}
