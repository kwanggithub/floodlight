package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class StringDataNodeSerializer implements DataNodeSerializer<String> {

    private static class InstanceHolder {
        private static final StringDataNodeSerializer INSTANCE =
                new StringDataNodeSerializer();
    }

    private StringDataNodeSerializer() {}

    public static StringDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(String s, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(s);
    }
}
