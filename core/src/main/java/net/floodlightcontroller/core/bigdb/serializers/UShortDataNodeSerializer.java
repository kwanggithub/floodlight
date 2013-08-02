package net.floodlightcontroller.core.bigdb.serializers;

import org.openflow.util.U16;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

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
