package org.projectfloodlight.db.yang;

import org.projectfloodlight.db.BigDBException;

public class ContainerStatement extends CompoundDataStatement {

    protected String presence;
    
    public ContainerStatement() {
        this(null);
    }
    
    public ContainerStatement(String name) {
        super(name);
    }
    
    public String getStatementType() {
        return "container";
    }

    public String getPresence() {
        return presence;
    }
    
    public void setPresence(String presence) {
        this.presence = presence;
    }
    
    public void accept(DataStatementVisitor visitor) throws BigDBException {
        visitor.visitEnter(this);
        for (DataStatement statement: getChildStatements()) {
            statement.accept(visitor);
        }
        visitor.visitLeave(this);
    }
}
