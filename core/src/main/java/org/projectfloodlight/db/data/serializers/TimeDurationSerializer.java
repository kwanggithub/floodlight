package org.projectfloodlight.db.data.serializers;

import org.projectfloodlight.core.types.TimeDuration;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

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
