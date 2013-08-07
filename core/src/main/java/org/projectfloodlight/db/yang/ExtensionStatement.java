package org.projectfloodlight.db.yang;

public class ExtensionStatement extends Statement
        implements Nameable, Describable, Statusable {

    protected String name;
    protected String argument;
    protected Status status;
    protected String description;
    protected String reference;
    
    public ExtensionStatement() {
        this(null);
    }
    
    public ExtensionStatement(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public String getArgument() {
        return argument;
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
    
    public void setArgument(String arg) {
        this.argument = arg;
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
