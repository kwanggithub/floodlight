package org.projectfloodlight.db.data.serializers;

import java.math.BigInteger;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class BigIntegerDataNodeSerializer implements DataNodeSerializer<BigInteger> {

    private static class InstanceHolder {
        private static final BigIntegerDataNodeSerializer INSTANCE =
                new BigIntegerDataNodeSerializer();
    }

    private BigIntegerDataNodeSerializer() {}

    public static BigIntegerDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(BigInteger bi, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeNumber(bi);
    }
}
