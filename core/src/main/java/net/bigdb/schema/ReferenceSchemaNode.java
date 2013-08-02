package net.bigdb.schema;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;

public class ReferenceSchemaNode extends SchemaNode {

    private String targetPath;
    private SchemaNode targetSchemaNode;
    
    public ReferenceSchemaNode() {
        this(null, null, null, null);
    }
    
    // FIXME: This probably shouldn't take a targetSchemaNode argument
    // since we'll probably need to resolve that later to handle forward
    // references.
    public ReferenceSchemaNode(String name, ModuleIdentifier module,
            String targetPath, SchemaNode targetSchemaNode) {
        super(name, module, NodeType.REFERENCE);
        this.targetPath = targetPath;
        this.targetSchemaNode = targetSchemaNode;
    }
    
    public String getTargetPath() {
        return targetPath;
    }
    
    public SchemaNode getTargetSchemaNode() {
        return targetSchemaNode;
    }

    public void setTargetSchemaNode(SchemaNode targetSchemaNode) {
        this.targetSchemaNode = targetSchemaNode;
    }

    @Override
    public void validate(DataNode dataNode) throws ValidationException {
        
    }
    
    @Override
    public SchemaNode getChildSchemaNode(String childNodeName)
            throws BigDBException {
        return targetSchemaNode.getChildSchemaNode(childNodeName);
    }

    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor)
            throws BigDBException {
        SchemaNodeVisitor.Result result = visitor.visit(this);
        return result;
    }
    
//    @Override
//    public SchemaNode clone() {
//        ReferenceSchemaNode schemaNode = (ReferenceSchemaNode) super.clone();
//        // The targetSchemaNode isn't really owned by this schema node, so it
//        // shouldn't be cloned, therefore we don't need to override anything
//        // to clone this class.
//        return schemaNode;
//    }
}
