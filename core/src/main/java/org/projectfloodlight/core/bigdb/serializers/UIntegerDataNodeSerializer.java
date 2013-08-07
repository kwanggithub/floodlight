package org.projectfloodlight.core.bigdb.serializers;

import org.openflow.util.U32;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class UIntegerDataNodeSerializer implements DataNodeSerializer<Integer> {

    @Override
    public void serialize(Integer i, DataNodeGenerator generator)
            throws BigDBException {
        if (i == null) {
            generator.writeNull();
        } else {
            generator.writeNumber(U32.f(i));
        }
    }
}
