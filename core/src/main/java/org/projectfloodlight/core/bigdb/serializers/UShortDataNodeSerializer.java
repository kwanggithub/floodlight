package org.projectfloodlight.core.bigdb.serializers;

import org.openflow.util.U16;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class UShortDataNodeSerializer implements DataNodeSerializer<Short> {

    @Override
    public void serialize(Short s, DataNodeGenerator generator)
            throws BigDBException {
        if (s == null) {
            generator.writeNull();
        } else {
            generator.writeNumber(U16.f(s));
        }
    }
}
