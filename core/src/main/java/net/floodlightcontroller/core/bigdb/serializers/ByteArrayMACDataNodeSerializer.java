package net.floodlightcontroller.core.bigdb.serializers;

import org.openflow.util.HexString;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class ByteArrayMACDataNodeSerializer implements DataNodeSerializer<byte[]> {

    @Override
    public void serialize(byte[] mac, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(HexString.toHexString(mac));
    }
}
