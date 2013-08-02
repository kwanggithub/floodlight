package net.bigdb.schema;

import net.bigdb.BigDBException;


public class ContainerSchemaNode extends AggregateSchemaNode {
    
    public ContainerSchemaNode() {
        this(null, null);
    }
    
    public ContainerSchemaNode(String name, ModuleIdentifier module) {
        super(name, module, NodeType.CONTAINER);
    }
    
     @Override
     public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor)
             throws BigDBException {
         SchemaNodeVisitor.Result result = visitor.visitEnter(this);
         if (result == SchemaNodeVisitor.Result.TERMINATE)
             return SchemaNodeVisitor.Result.TERMINATE;
         if (result != SchemaNodeVisitor.Result.SKIP_SUBTREE) {
             result = acceptChildNodes(visitor);
             if (result == SchemaNodeVisitor.Result.TERMINATE)
                 return SchemaNodeVisitor.Result.TERMINATE;
         }
         result = visitor.visitLeave(this);
         return result;
     }
}
