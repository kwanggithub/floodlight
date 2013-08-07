package org.projectfloodlight.db.data.serializers;

import java.util.Iterator;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;
import org.projectfloodlight.db.data.DataNodeSerializerRegistry;

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
