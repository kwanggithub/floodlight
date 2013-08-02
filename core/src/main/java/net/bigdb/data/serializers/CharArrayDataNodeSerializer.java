package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class CharArrayDataNodeSerializer implements DataNodeSerializer<char[]> {

    private static class InstanceHolder {
        private static final CharArrayDataNodeSerializer INSTANCE =
                new CharArrayDataNodeSerializer();
    }

    private CharArrayDataNodeSerializer() {}

    public static CharArrayDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(char[] charArray, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeListStart();
        for (char c: charArray)
            generator.writeString(String.valueOf(c));
        generator.writeListEnd();
    }
}
