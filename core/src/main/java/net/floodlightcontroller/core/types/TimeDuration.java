package net.floodlightcontroller.core.types;

import net.bigdb.data.annotation.BigDBSerialize;
import net.bigdb.data.serializers.TimeDurationSerializer;

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
