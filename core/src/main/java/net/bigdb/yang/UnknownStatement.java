package net.bigdb.yang;

import net.bigdb.BigDBException;

/**
 * FIXME: UnknownStatement should extend Statement.
 * The statement tree should contain all statement.
 * RobV: No, the statement tree should only contain data statement.
 * UnknownStatement is not a data statement.
 *
 * @author kevinwang
 *
 */
public class UnknownStatement extends DataStatement {
    
    protected String prefix;
    // FIXME: Currently since this extends DataStatement we don't want to
    // define "name" here because then it hides the "name" field int
    // DataStatement. So for now we'll just change this to use the "name"
    // field from DataStatement. When we fix the code so that this class
    // extends Statement and not DataStatement, then we'll define the "name"
    // field here.
    //protected String name;
    protected String arg;
    
    public UnknownStatement() {
        this(null, null);
    }
    
    public UnknownStatement(String prefix, String name) {
        super(name);
        this.prefix = prefix;
        //this.name = name;
    }
    
// See the FIXME comment for the "name" field above.
//    public String getName() {
//        return name;
//    }
//
//    public void setName(String name) {
//        this.name = name;
//    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
    public String getArg() {
        return arg;
    }
    
    public void setArg(String arg) {
        this.arg = arg;
    }

    @Override
    public String getStatementType() {
        return "unknown";
    }

    @Override
    public void accept(DataStatementVisitor visitor) throws BigDBException {
        visitor.visit(this);
        
    }
}
