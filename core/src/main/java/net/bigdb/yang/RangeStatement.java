package net.bigdb.yang;

import java.util.List;
import java.util.ArrayList;

public class RangeStatement extends RestrictionStatement {

    protected List<RangePart> rangeParts = new ArrayList<RangePart>();
    
    public RangeStatement() {
        this(null);
    }
    
    public RangeStatement(List<RangePart> rangeParts) {
        this.rangeParts = rangeParts;
    }
    
    public List<RangePart> getRangeParts() {
        return rangeParts;
    }
    
    public void addRangePart(RangePart rangePart) {
        rangeParts.add(rangePart);
    }
    
    public void setRangeParts(List<RangePart> rangeParts) {
        this.rangeParts = rangeParts;
    }
}
