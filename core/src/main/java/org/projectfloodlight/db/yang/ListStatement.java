package org.projectfloodlight.db.yang;

import org.projectfloodlight.db.BigDBException;

public class ListStatement extends CompoundDataStatement {
    
    protected String key;
    protected long minElements;
    protected long maxElements;
    
    public ListStatement() {
        super();
    }
    
    public ListStatement(String name) {
        super(name);
    }
    
    public String getStatementType() {
        return "list";
    }

    public String getKey() {
        return key;
    }
    
    public long getMinElements() {
        return minElements;
    }
    
    public long getMaxElements() {
        return maxElements;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public void setMinElements(long minElements) {
        this.minElements = minElements;
    }
    
    public void setMaxElements(long maxElements) {
        this.maxElements = maxElements;
    }
    
    public void accept(DataStatementVisitor visitor) throws BigDBException  {
        visitor.visitEnter(this);
        for (DataStatement statement: getChildStatements()) {
            statement.accept(visitor);
        }
        visitor.visitLeave(this);
    }
}
