package net.floodlightcontroller.core.bigdb.serializers;

import org.openflow.util.U32;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

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
