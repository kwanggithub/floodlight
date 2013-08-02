package net.bigdb.yang;

public class BelongsToStatement extends Statement {

    protected String name;
    protected String prefix;
    
    public BelongsToStatement() {
        this(null);
    }
    
    public BelongsToStatement(String name) {
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
}
