package org.projectfloodlight.db.yang;

import org.projectfloodlight.db.BigDBException;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class DataStatement extends Statement
        implements Nameable, Describable, Configable, Statusable {

    protected CompoundDataStatement parentNode;
    protected String name;
    protected WhenStatement when;
    protected String description;
    protected String reference;
    protected Boolean config;
    protected Status status;
    
    public DataStatement() {
        this(null);
    }
    
    public DataStatement(String name) {
        this.setName(name);
    }
    
    @JsonIgnore
    public CompoundDataStatement getParentNode() {
        return parentNode;
    }
    
    public String getName() {
        return name;
    }
    
    abstract public String getStatementType();

    public WhenStatement getWhen() {
        return when;
    }
    
    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }
    
    public Boolean getConfig() {
        return config;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public void setParentNode(CompoundDataStatement parentNode) {
        this.parentNode = parentNode;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setWhen(WhenStatement when) {
        this.when = when;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
    
    public void setConfig(Boolean config) {
        this.config = config;
    }
    
    public void setStatus(Status status) {
        this.status = status;
    }
    
    public abstract void accept(DataStatementVisitor visitor) throws BigDBException;
}
