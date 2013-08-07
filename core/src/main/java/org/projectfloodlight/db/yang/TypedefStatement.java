package org.projectfloodlight.db.yang;

public class TypedefStatement extends Statement
        implements Nameable, Describable, Statusable, Typeable {
    
    protected String name;
    protected TypeStatement type;
    protected String units;
    protected String defaultValue;
    protected Status status;
    protected String description;
    protected String reference;
    
    public TypedefStatement() {
        this(null);
    }
    
    public TypedefStatement(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
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
    
    public Status getStatus() {
        return status;
    }
    
    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public void setName(String name) {
        this.name = name;
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
    
    public void setStatus(Status status) {
        this.status = status;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}
