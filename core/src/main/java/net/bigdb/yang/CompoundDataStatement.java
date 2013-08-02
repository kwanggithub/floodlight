package net.bigdb.yang;

import java.util.List;
import java.util.ArrayList;

public abstract class CompoundDataStatement extends DataStatement {

    protected List<DataStatement> childStatements;
    
    public CompoundDataStatement() {
        this(null);
    }
    
    public CompoundDataStatement(String name) {
        super(name);
        childStatements = new ArrayList<DataStatement>();
    }
    
    public List<DataStatement> getChildStatements() {
        return childStatements;
    }
    
    public void addChildStatement(DataStatement childStatement) {
        childStatements.add(childStatement);
        childStatement.setParentNode(this);
    }
    
    public DataStatement findChildStatement(String name, boolean caseSensitive) {
        assert name != null;
        for (DataStatement statement: childStatements) {
            if (caseSensitive) {
                if (name.equals(statement.getName()))
                    return statement;
            } else {
                if (name.equalsIgnoreCase(statement.getName()))
                    return statement;
            }
        }
        return null;
    }
}
