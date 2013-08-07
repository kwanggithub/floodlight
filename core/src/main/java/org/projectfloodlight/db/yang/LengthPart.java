package org.projectfloodlight.db.yang;

public class LengthPart {
    
    protected LengthBoundary start;
    protected LengthBoundary end;
    
    public LengthPart() {
        this(null, null);
    }
    
    public LengthPart(LengthBoundary value) {
        this(value, value);
    }
    
    public LengthPart(LengthBoundary start, LengthBoundary end) {
        this.start = start;
        this.end = end;
    }
    
    public LengthBoundary getStart() {
        return start;
    }
    
    public LengthBoundary getEnd() {
        return end;
    }

    public void set(LengthBoundary start, LengthBoundary end) {
        this.start = start;
        this.end = end;
    }
    
    public void setStart(LengthBoundary start) {
        this.start = start;
    }
    
    public void setEnd(LengthBoundary end) {
        this.end = end;
    }
    
    public String toString() {
        String startStr = (start != null) ? start.toString() : "null";
        String endStr = (end != null) ? end.toString() : "null";
        return startStr.equals(endStr) ? startStr : startStr + ".." + endStr;
    }
}
