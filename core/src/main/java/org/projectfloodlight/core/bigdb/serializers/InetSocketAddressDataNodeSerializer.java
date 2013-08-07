package org.projectfloodlight.core.bigdb.serializers;

import java.net.InetSocketAddress;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

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
