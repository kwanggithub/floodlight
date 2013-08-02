package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

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
