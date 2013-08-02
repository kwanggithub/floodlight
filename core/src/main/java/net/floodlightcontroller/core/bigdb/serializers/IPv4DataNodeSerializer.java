package net.floodlightcontroller.core.bigdb.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;
import net.floodlightcontroller.packet.IPv4;

public class IPv4DataNodeSerializer implements DataNodeSerializer<Integer> {

    @Override
    public void serialize(Integer ip, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(ip == null? "" : IPv4.fromIPv4Address(ip));
    }
}
