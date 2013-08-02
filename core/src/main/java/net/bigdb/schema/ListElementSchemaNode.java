package net.bigdb.schema;

import java.util.ArrayList;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.schema.SchemaNodeVisitor.Result;

public class ListElementSchemaNode extends AggregateSchemaNode {

    protected List<String> keyNodeNames = new ArrayList<String>();
    
    public ListElementSchemaNode() {
        this(null);
    }
    
    public ListElementSchemaNode(ModuleIdentifier moduleId) {
        super("", moduleId, NodeType.LIST_ELEMENT);
    }
    
    @Override
    public Result accept(SchemaNodeVisitor visitor) throws BigDBException {
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

    public List<String> getKeyNodeNames() {
        return keyNodeNames;
    }
    
    public void addKeyNodeName(String keyNodeName) {
        keyNodeNames.add(keyNodeName);
    }
    
    @Override
    public SchemaNode clone() {
        ListElementSchemaNode schemaNode = (ListElementSchemaNode) super.clone();
        // Doubtful the keys would ever change, but just to be safe...
        schemaNode.keyNodeNames = new ArrayList<String>(keyNodeNames);
        return schemaNode;
    }
}
