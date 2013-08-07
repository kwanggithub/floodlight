package org.projectfloodlight.db.schema;

public class Range<T extends Comparable<T>> {

    public T MIN = null;
    public T MAX = null;
    
    private T start;
    private T end;

    public Range() {
    }
    
    public Range(T start, T end) {
        this.start = start;
        this.end = end;
    }
    
    public Range(T value) {
        this(value, value);
    }
    
    public T getStart() {
        return start;
    }
    
    public T getEnd() {
        return end;
    }
    
    public void setStart(T start) {
        this.start = start;
    }
    
    public void setEnd(T end) {
        this.end = end;
    }
    
    public void set(T start, T end) {
        this.start = start;
        this.end = end;
    }
    
    public boolean isInRange(Comparable<T> value) {
        assert value != null;
        return ((start == null) || (value.compareTo(start) >= 0)) &&
                ((end == null) || (value.compareTo(end) <= 0));
    }
}
