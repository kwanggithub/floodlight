package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

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
