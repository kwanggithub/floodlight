package net.floodlightcontroller.core.bigdb.serializers;

import java.net.InetSocketAddress;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class InetSocketAddressDataNodeSerializer implements
        DataNodeSerializer<InetSocketAddress> {

    @Override
    public void serialize(InetSocketAddress address, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeMapStart();
        generator.writeStringField("ip", address.getAddress().getHostAddress());
        generator.writeNumberField("inet-port", address.getPort());
        generator.writeMapEnd();
    }
}
