package net.bigdb.yang;

public class ModuleStatement extends ModuleStatementCommon {
    
    protected String namespace;
    protected String prefix;
    
    public ModuleStatement() {
        super();
    }
    
    public ModuleStatement(String name) {
        super(name);
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
