package org.projectfloodlight.db.schema;

import org.projectfloodlight.db.BigDBException;

public class TypedefSchemaNode extends ScalarSchemaNode 
        implements SchemaTypeable {

    public TypedefSchemaNode() {
        super();
    }
    
    public TypedefSchemaNode(String name, ModuleIdentifier module) {
        super(name, module);
    }
    
    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor)
            throws BigDBException {
        SchemaNodeVisitor.Result result = visitor.visit(this);
        return result;
    }
    
    @Override
    public TypeSchemaNode getTypeSchemaNode() {
        return this.getBaseTypedef();
    }
}
