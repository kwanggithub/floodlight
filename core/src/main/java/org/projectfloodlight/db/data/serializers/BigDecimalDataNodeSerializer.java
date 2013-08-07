package org.projectfloodlight.db.data.serializers;

import java.math.BigDecimal;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

public class BigDecimalDataNodeSerializer implements DataNodeSerializer<BigDecimal> {

    private static class InstanceHolder {
        private static final BigDecimalDataNodeSerializer INSTANCE =
                new BigDecimalDataNodeSerializer();
    }

    private BigDecimalDataNodeSerializer() {}

    public static BigDecimalDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(BigDecimal bd, DataNodeGenerator generator)
            throws BigDBException {
        generator.writeNumber(bd);
    }
}
