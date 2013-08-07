package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;

public class ISOLongDateDataNodeSerializer extends AbstractISODateDataNodeSerializer<Long> {

    @Override
    public void serialize(Long d, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(AbstractISODateDataNodeSerializer.formatISO(d));
    }
}
