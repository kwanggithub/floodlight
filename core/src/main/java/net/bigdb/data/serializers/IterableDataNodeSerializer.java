package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

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
