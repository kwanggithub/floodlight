package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class IterableDataNodeSerializer implements
        DataNodeSerializer<Iterable<?>> {

    private static class InstanceHolder {
        private static final IterableDataNodeSerializer INSTANCE =
                new IterableDataNodeSerializer();
    }

    private IterableDataNodeSerializer() {
    }

    public static IterableDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Iterable<?> iterable, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeIterable(iterable);
    }
}
