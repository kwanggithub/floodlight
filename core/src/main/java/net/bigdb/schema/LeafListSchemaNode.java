package net.bigdb.schema;

import net.bigdb.BigDBException;

public class LeafListSchemaNode extends SchemaNode {

    protected LeafSchemaNode leafSchemaNode;
    
    public LeafListSchemaNode() {
        super();
    }
    
    public LeafListSchemaNode(String name, ModuleIdentifier moduleId,
            LeafSchemaNode leafSchemaNode) {
        super(name, moduleId, NodeType.LEAF_LIST);
        this.leafSchemaNode = leafSchemaNode;
        leafSchemaNode.parent = this;
    }
    
    public LeafSchemaNode getLeafSchemaNode() {
        return leafSchemaNode;
    }
    
    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor)
            throws BigDBException {
        SchemaNodeVisitor.Result result = visitor.visit(this);
        return result;
    }
    
    @Override
    public LeafListSchemaNode clone() {
        LeafListSchemaNode schemaNode = (LeafListSchemaNode) super.clone();
        schemaNode.leafSchemaNode = leafSchemaNode.clone();
        return schemaNode;
    }

//    @Override
//    public TypeSchemaNode getTypeSchemaNode() {
//        return this.leafSchemaNode.getBaseTypedef();
//    }   
    
}
