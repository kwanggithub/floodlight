package net.bigdb.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;

import net.bigdb.BigDBException;

public class LeafSchemaNode extends ScalarSchemaNode 
            implements SchemaTypeable {
    
    public LeafSchemaNode() {
        this(null, null);
    }
    
    public LeafSchemaNode(String name, ModuleIdentifier module) {
        super(name, module);
    }
    
    public LeafSchemaNode(String name, ModuleIdentifier module,
            LeafType leafType) {
        super(name, module, leafType);
    }
    
    public boolean getMandatory() {
        return this.mandatory;
    }
    
    public void setMandatory(boolean m) {
        this.mandatory = m;
    }
    
    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor)
            throws BigDBException {
        SchemaNodeVisitor.Result result = visitor.visit(this);
        return result;
    }
    
    @Override
    public LeafSchemaNode clone() {
        return (LeafSchemaNode) super.clone();
    }

    @Override
    public TypeSchemaNode getTypeSchemaNode() {
        return this.getBaseTypedef();
    }

    @JsonIgnore
    public boolean isKey() {
        SchemaNode parentSchemaNode = getParentSchemaNode();
        if (!(parentSchemaNode instanceof ListElementSchemaNode))
            return false;
        ListElementSchemaNode listElementSchemaNode = (ListElementSchemaNode) parentSchemaNode;
        return listElementSchemaNode.getKeyNodeNames().contains(getName());
    }
}
