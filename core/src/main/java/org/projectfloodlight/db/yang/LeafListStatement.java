package org.projectfloodlight.db.yang;

import org.projectfloodlight.db.BigDBException;

public class LeafListStatement extends DataStatement
        implements Typeable {
    
    protected TypeStatement type;
    protected String units;
    protected long minElements;
    protected long maxElements;
    
    public LeafListStatement() {
        super();
    }
    
    public LeafListStatement(String name) {
        super(name);
    }

    public String getStatementType() {
        return "leaf-list";
    }

    @Override
    public TypeStatement getType() {
        return type;
    }
    
    @Override
    public String getUnits() {
        return units;
    }
    
    @Override
    public String getDefault() {
        // Leaf list nodes don't have defaults but we need to provide this
        // to implement all of the Typeable interface
        return null;
    }
    
    public long getMinElements() {
        return minElements;
    }
    
    public long getMaxElements() {
        return maxElements;
    }
    
    @Override
    public void setType(TypeStatement type) {
        this.type = type;
    }
    
    @Override
    public void setUnits(String units) {
        this.units = units;
    }

    @Override
    public void setDefault(String defaultValue) {
        throw new UnsupportedOperationException(
                "Leaf list can't have a default value");
    }
    public void setMinElements(long minElements) {
        this.minElements = minElements;
    }
    
    public void setMaxElements(long maxElements) {
        this.maxElements = maxElements;
    }
    
    public void accept(DataStatementVisitor visitor) throws BigDBException {
        visitor.visit(this);
    }
}
