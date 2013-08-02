package net.bigdb.yang;

public class SubmoduleStatement extends ModuleStatementCommon {

    protected BelongsToStatement belongsTo;
    
    public SubmoduleStatement() {
        super();
    }
    
    public SubmoduleStatement(String name) {
        super(name);
    }
    
    public BelongsToStatement getBelongsTo() {
        return belongsTo;
    }
    
    public void setBelongsTo(BelongsToStatement belongsTo) {
        this.belongsTo = belongsTo;
    }
}
