package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class BooleanArrayDataNodeSerializer implements DataNodeSerializer<boolean[]> {

    private static class InstanceHolder {
        private static final BooleanArrayDataNodeSerializer INSTANCE =
                new BooleanArrayDataNodeSerializer();
    }

    private BooleanArrayDataNodeSerializer() {}

    public static BooleanArrayDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(boolean[] booleanArray, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeListStart();
        for (boolean b: booleanArray)
            generator.writeBoolean(b);
        generator.writeListEnd();
    }
}
