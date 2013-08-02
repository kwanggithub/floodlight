package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class UnsignedShortSerializer implements DataNodeSerializer<Short> {

    @Override
    public void serialize(Short s, DataNodeGenerator gen)
            throws BigDBException {
        if (s == null) gen.writeNull();
        else gen.writeNumber(s.shortValue() & 0xffff);
    }

}
