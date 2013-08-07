package org.projectfloodlight.db.data.serializers;

import java.util.Date;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;

public class ISODateDataNodeSerializer extends AbstractISODateDataNodeSerializer<Date> {

    @Override
    public void serialize(Date d, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(AbstractISODateDataNodeSerializer.formatISO(d));
    }
}
