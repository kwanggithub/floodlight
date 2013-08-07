package org.projectfloodlight.db.yang;

public class RevisionStatement extends Statement {

    protected String revisionDate;
    protected String description;
    protected String reference;
    
    public RevisionStatement() {
        this(null);
    }
    
    public RevisionStatement(String revisionDate) {
        this.revisionDate = revisionDate;
    }
    
    public String getRevisionDate() {
        return revisionDate;
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
