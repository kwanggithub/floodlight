package org.projectfloodlight.core.bigdb.serializers;

import org.openflow.util.HexString;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class DPIDDataNodeSerializer implements DataNodeSerializer<Long> {

    @Override
    public void serialize(Long dpid, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(HexString.toHexString(dpid, 8));
    }
}
