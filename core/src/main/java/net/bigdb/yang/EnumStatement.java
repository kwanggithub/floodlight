package net.bigdb.yang;

public class EnumStatement extends Statement
        implements Statusable, Describable {

    protected String name;
    protected Long value;
    protected Status status;
    protected String description;
    protected String reference;
    
    public EnumStatement() {
        this(null);
    }
    
    public EnumStatement(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public Long getValue() {
        return value;
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
    
    public void setValue(Long value) {
        this.value = value;
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
