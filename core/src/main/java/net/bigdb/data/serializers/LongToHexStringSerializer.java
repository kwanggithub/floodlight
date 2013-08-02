package net.bigdb.data.serializers;

import org.openflow.util.HexString;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class LongToHexStringSerializer implements DataNodeSerializer<Long> {

    @Override
    public void serialize(Long l, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(HexString.toHexString(l));
    }

}
