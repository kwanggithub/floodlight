package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class SelfDataNodeSerializer implements
        DataNodeSerializer<DataNodeSerializer<Object>> {

    private static class InstanceHolder {
        private static final SelfDataNodeSerializer INSTANCE =
                new SelfDataNodeSerializer();
    }

    private SelfDataNodeSerializer() {}

    public static SelfDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(DataNodeSerializer<Object> object,
            DataNodeGenerator generator) throws BigDBException {
        object.serialize(object, generator);
    }
}
