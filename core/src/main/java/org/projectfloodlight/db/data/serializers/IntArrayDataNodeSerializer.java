package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class IntArrayDataNodeSerializer implements DataNodeSerializer<int[]> {

    private static class InstanceHolder {
        private static final IntArrayDataNodeSerializer INSTANCE =
                new IntArrayDataNodeSerializer();
    }

    private IntArrayDataNodeSerializer() {}

    public static IntArrayDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(int[] intArray, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeListStart();
        for (int i: intArray)
            generator.writeNumber(i);
        generator.writeListEnd();
    }
}
