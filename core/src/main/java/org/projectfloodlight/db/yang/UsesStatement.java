package org.projectfloodlight.db.yang;

import org.projectfloodlight.db.BigDBException;

public class UsesStatement extends DataStatement {
    
    private String prefix = null;

    public UsesStatement() {
        super();
    }
    
    public UsesStatement(String name) {
        super(name);
    }
    
    public String getStatementType() {
        return "uses";
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void accept(DataStatementVisitor visitor) throws BigDBException  {
        visitor.visit(this);
    }
}
