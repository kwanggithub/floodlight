package net.bigdb.schema;

import java.util.Map;

import net.bigdb.BigDBException;

public class GroupingSchemaNode extends AggregateSchemaNode {
    
    public GroupingSchemaNode() {
        this(null, null);
    }
    
    public GroupingSchemaNode(String name, ModuleIdentifier module) {
        super(name, module, NodeType.GROUPING);
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
    
    @Override
    public SchemaNode clone() {
        // should not be here
        throw new AssertionError("Grouping schema node shouldn't be cloned.");
    }
    
    /**
     * Used when resolving the use schema node, where all child nodes need
     * to be expanded and copied into the containing node.
     * 
     * @param nodes 
     */
    public void copyChildNodes(Map<String, SchemaNode> nodes) {
        // making a deep copy of the child nodes
        for (Map.Entry<String, SchemaNode> e : this.childNodes.entrySet()) {
            // will throw exception if there is any UsesSchemaNode
            nodes.put(e.getKey(), e.getValue().clone());
        }
    }
}
