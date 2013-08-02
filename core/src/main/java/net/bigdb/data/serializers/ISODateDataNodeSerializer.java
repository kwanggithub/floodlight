package net.bigdb.data.serializers;

import java.util.Date;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;

public class ISODateDataNodeSerializer extends AbstractISODateDataNodeSerializer<Date> {

    @Override
    public void serialize(Date d, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(AbstractISODateDataNodeSerializer.formatISO(d));
    }
}
