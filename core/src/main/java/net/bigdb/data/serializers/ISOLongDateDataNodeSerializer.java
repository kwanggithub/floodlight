package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;

public class ISOLongDateDataNodeSerializer extends AbstractISODateDataNodeSerializer<Long> {

    @Override
    public void serialize(Long d, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeString(AbstractISODateDataNodeSerializer.formatISO(d));
    }
}
