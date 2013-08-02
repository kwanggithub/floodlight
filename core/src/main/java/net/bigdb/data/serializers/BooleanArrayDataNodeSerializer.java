package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

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
