package net.bigdb.data.serializers;

import java.math.BigInteger;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

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
