package net.bigdb.yang;

public class IncludeStatement extends Statement {

    protected String name;
    protected String revisionDate;
    
    public IncludeStatement() {
        this(null);
    }
    
    public IncludeStatement(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getRevisionDate() {
        return revisionDate;
    }
    
    public void setRevisionDate(String revisionDate) {
        this.revisionDate = revisionDate;
    }
}
