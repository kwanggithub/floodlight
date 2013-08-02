package net.bigdb.yang;

import net.bigdb.BigDBException;

public class LeafStatement extends DataStatement
        implements Typeable {
    
    protected TypeStatement type;
    protected String units;
    protected String defaultValue;
    protected Boolean mandatory;
    
    public LeafStatement() {
        super();
    }
    
    public LeafStatement(String name) {
        super(name);
    }
    
    public String getStatementType() {
        return "leaf";
    }

    public TypeStatement getType() {
        return type;
    }
    
    public String getUnits() {
        return units;
    }
    
    public String getDefault() {
        return defaultValue;
    }
    
    public Boolean getMandatory() {
        return mandatory;
    }
    
    public void setType(TypeStatement type) {
        this.type = type;
    }
    
    public void setUnits(String units) {
        this.units = units;
    }
    
    public void setDefault(String defaultValue) {
        this.defaultValue = defaultValue;
    }
    
    public void setMandatory(Boolean mandatory) {
        this.mandatory = mandatory;
    }
    
    public void accept(DataStatementVisitor visitor) throws BigDBException  {
        visitor.visit(this);
    }
}
