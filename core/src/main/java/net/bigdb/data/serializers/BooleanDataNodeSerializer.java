package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

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
