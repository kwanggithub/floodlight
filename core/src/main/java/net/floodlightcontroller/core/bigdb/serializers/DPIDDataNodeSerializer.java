package net.floodlightcontroller.core.bigdb.serializers;

import org.openflow.util.HexString;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class DPIDDataNodeSerializer implements DataNodeSerializer<Long> {

    @Override
    public void serialize(Long dpid, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(HexString.toHexString(dpid, 8));
    }
}
