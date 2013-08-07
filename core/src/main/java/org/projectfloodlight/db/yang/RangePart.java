package org.projectfloodlight.db.yang;

public class RangePart {
    
    protected RangeBoundary start;
    protected RangeBoundary end;
    
    public RangePart() {
        this(null, null);
    }
    
    public RangePart(RangeBoundary value) {
        this(value, value);
    }
    
    public RangePart(RangeBoundary start, RangeBoundary end) {
        this.start = start;
        this.end = end;
    }
    
    public RangeBoundary getStart() {
        return start;
    }
    
    public RangeBoundary getEnd() {
        return end;
    }

    public void set(RangeBoundary start, RangeBoundary end) {
        this.start = start;
        this.end = end;
    }
    
    public void setStart(RangeBoundary start) {
        this.start = start;
    }
    
    public void setEnd(RangeBoundary end) {
        this.end = end;
    }
    
    public String toString() {
        String startStr = (start != null) ? start.toString() : "null";
        String endStr = (end != null) ? end.toString() : "null";
        return startStr.equals(endStr) ? startStr : startStr + ".." + endStr;
    }
}
