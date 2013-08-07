package org.projectfloodlight.core.bigdb.serializers;

import org.openflow.util.HexString;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class MACDataNodeSerializer implements DataNodeSerializer<Long> {

    @Override
    public void serialize(Long mac, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(HexString.toHexString(mac, 6));
    }
}
