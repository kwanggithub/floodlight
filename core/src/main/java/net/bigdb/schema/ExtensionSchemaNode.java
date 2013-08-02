package net.bigdb.schema;

import net.bigdb.BigDBException;


public class ExtensionSchemaNode extends SchemaNode {
    String argument;
    
    public ExtensionSchemaNode(String name, String argument, ModuleIdentifier module) {
        super(name, module, NodeType.USES);
        this.argument = argument;
    }
    
    public String getArgument() {
        return this.argument;
    }
    
    public void setArgument(String arg) {
        this.argument = arg;
    }
    
    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor) 
            throws BigDBException {
        //TODO: need to examine
        return SchemaNodeVisitor.Result.CONTINUE;
    }
}
