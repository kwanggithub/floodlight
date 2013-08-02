package net.bigdb.yang;

import java.util.ArrayList;
import java.util.List;

public class LengthStatement extends RestrictionStatement {

    protected List<LengthPart> lengthParts = new ArrayList<LengthPart>();
    
    public List<LengthPart> getLengthParts() {
        return lengthParts;
    }
    
    public void addLengthPart(LengthPart lengthPart) {
        lengthParts.add(lengthPart);
    }
    
    public void setLengthParts(List<LengthPart> lengthParts) {
        this.lengthParts = lengthParts;
    }
}
