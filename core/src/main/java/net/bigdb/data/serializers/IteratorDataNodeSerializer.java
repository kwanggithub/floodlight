package net.bigdb.data.serializers;

import java.util.Iterator;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeSerializerRegistry;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class IteratorDataNodeSerializer implements
        DataNodeSerializer<Iterator<?>> {

    private static class InstanceHolder {
        private static final IteratorDataNodeSerializer INSTANCE =
                new IteratorDataNodeSerializer();
    }

    private IteratorDataNodeSerializer() {
    }

    public static IteratorDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Iterator<?> iterator, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeListStart();
        while (iterator.hasNext()) {
            Object object = iterator.next();
            DataNodeSerializerRegistry.serializeObject(object, generator);
        }
        generator.writeListEnd();
    }
}
