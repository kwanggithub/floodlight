package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class UnsignedShortSerializer implements DataNodeSerializer<Short> {

    @Override
    public void serialize(Short s, DataNodeGenerator gen)
            throws BigDBException {
        if (s == null) gen.writeNull();
        else gen.writeNumber(s.shortValue() & 0xffff);
    }

}
