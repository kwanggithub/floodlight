package net.bigdb.yang;

public class RestrictionStatement extends Statement {
    
    protected String description;
    protected String reference;
    protected String errorMessage;
    protected String errorAppTag;
    
    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
    
    public String getErrorAppTag() {
        return errorAppTag;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public void setErrorAppTag(String errorAppTag) {
        this.errorAppTag = errorAppTag;
    }
}
