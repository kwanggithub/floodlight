package net.bigdb.data.serializers;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;
import net.floodlightcontroller.core.types.TimeDuration;

/**
 * 
 * @author alexreimers
 *
 */
public class TimeDurationSerializer implements DataNodeSerializer<TimeDuration> {

    @Override
    public void serialize(TimeDuration td, DataNodeGenerator gen) throws BigDBException {
        // FIXME: RobV: This probably shouldn't include the writeMapFieldStart
        // and writeMapFieldEnd calls that hard-code the name of the container
        // to be "duration".
        //gen.writeMapFieldStart("duration");
        gen.writeMapStart();
        gen.writeNumberField("sec", td.getSeconds());
        gen.writeNumberField("nsec", td.getNanoSeconds());
        gen.writeMapEnd();
    }
}
