package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

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
