package org.projectfloodlight.db.yang;

public class ImportStatement extends Statement implements Nameable {

    protected String name;
    protected String prefix;
    protected String revisionDate;
    
    public ImportStatement() {
        this(null);
    }
    
    public ImportStatement(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getRevisionDate() {
        return revisionDate;
    }
    
    public void setRevisionDate(String revisionDate) {
        this.revisionDate = revisionDate;
    }
}
