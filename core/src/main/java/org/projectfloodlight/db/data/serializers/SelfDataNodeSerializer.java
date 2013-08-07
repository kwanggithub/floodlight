package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

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
