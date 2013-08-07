package org.projectfloodlight.db.yang;

public class WhenStatement extends Statement {

    protected String name;
    protected String description;
    protected String reference;
    
    public WhenStatement() {
        this(null);
    }
    
    public WhenStatement(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
