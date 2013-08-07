package org.projectfloodlight.core.types;

import org.projectfloodlight.db.data.annotation.BigDBSerialize;
import org.projectfloodlight.db.data.serializers.TimeDurationSerializer;

/**
 * A time duration in the format <sec>.<nsec>.
 * @author alexreimers
 *
 */
@BigDBSerialize(using=TimeDurationSerializer.class)
public class TimeDuration {
    private int seconds;
    private int nanoSeconds;
    
    public TimeDuration(int sec, int nsec) {
        this.seconds = sec;
        this.nanoSeconds = nsec;
    }
    
    public int getSeconds() {
        return seconds;
    }
    
    public int getNanoSeconds() {
        return nanoSeconds;
    }
    
    @Override
    public String toString() {
        return seconds + "." + nanoSeconds;
    }
}
