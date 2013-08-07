package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

/**
 * A BigDB serializer for Enums. It calls the toString() method on the enum
 * and changes underscores to dashes and makes everything lowercase.
 *
 * @author alexreimers
 * @author rob.vaterlaus@bigswitch.com
 */
@SuppressWarnings("rawtypes")
public class EnumDataNodeSerializer implements DataNodeSerializer<Enum> {

    private static class InstanceHolder {
        private static final EnumDataNodeSerializer INSTANCE =
                new EnumDataNodeSerializer();
    }

    private EnumDataNodeSerializer() {}

    public static EnumDataNodeSerializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public void serialize(Enum e, DataNodeGenerator gen) throws BigDBException {
        // FIXME: Do we still need to check for null here?
        if (e == null) {
            gen.writeNull();
        } else {
            gen.writeString(e.toString().toLowerCase().replace('_', '-'));
        }
    }
}
