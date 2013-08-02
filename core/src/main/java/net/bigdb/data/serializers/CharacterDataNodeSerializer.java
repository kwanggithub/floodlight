package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class CharacterDataNodeSerializer implements DataNodeSerializer<Character> {

    private static class InstanceHolder {
        private static final CharacterDataNodeSerializer INSTANCE =
                new CharacterDataNodeSerializer();
    }

    private CharacterDataNodeSerializer() {}

    public static CharacterDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Character c, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(c.toString());
    }
}
