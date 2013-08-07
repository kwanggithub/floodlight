package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

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
