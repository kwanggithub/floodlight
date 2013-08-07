package org.projectfloodlight.db.data.serializers;

import org.openflow.util.HexString;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class LongToHexStringSerializer implements DataNodeSerializer<Long> {

    @Override
    public void serialize(Long l, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(HexString.toHexString(l));
    }

}
