package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class BooleanDataNodeSerializer implements DataNodeSerializer<Boolean> {

    private static class InstanceHolder {
        private static final BooleanDataNodeSerializer INSTANCE =
                new BooleanDataNodeSerializer();
    }

    private BooleanDataNodeSerializer() {}

    public static BooleanDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Boolean b, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeBoolean(b);
    }
}
