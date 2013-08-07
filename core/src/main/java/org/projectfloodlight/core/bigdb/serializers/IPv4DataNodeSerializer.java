package org.projectfloodlight.core.bigdb.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;
import org.projectfloodlight.packet.IPv4;

public class IPv4DataNodeSerializer implements DataNodeSerializer<Integer> {

    @Override
    public void serialize(Integer ip, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(ip == null? "" : IPv4.fromIPv4Address(ip));
    }
}
