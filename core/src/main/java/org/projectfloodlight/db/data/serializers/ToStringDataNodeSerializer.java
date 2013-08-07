package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

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
