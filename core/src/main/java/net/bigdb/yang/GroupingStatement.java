package net.bigdb.yang;

import net.bigdb.BigDBException;

public class GroupingStatement extends ContainerStatement {

    //protected String presence;
    
    public GroupingStatement() {
        this(null);
    }
    
    public GroupingStatement(String name) {
        super(name);
    }
    @Override
    public String getStatementType() {
        return "grouping";
    }
    
    public void accept(DataStatementVisitor visitor) throws BigDBException {
        visitor.visitEnter(this);
        for (DataStatement statement: getChildStatements()) {
            statement.accept(visitor);
        }
        visitor.visitLeave(this);
    }
}
