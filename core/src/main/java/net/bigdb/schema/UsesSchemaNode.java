package net.bigdb.schema;

import java.util.Map;
import java.util.TreeMap;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;

/**
 * UsesSchemaNode is used to track the uses statement as a temp placeholder 
 * for later resolution. It will be replaced by it contents. However
 * for each agregateSchemNode, a list of uses statements are kept
 * for later references.
 * @author kevinwang
 *
 */
public class UsesSchemaNode extends SchemaNode {
    private Map<String, SchemaNode> usedSchemaNodes = 
            new TreeMap<String, SchemaNode>();
    private String prefix = null;
    
    public UsesSchemaNode(String name, String prefix, ModuleIdentifier module) {
        super(name, module, NodeType.USES);
        this.prefix = prefix;
    }
    
    public String getPrefix() {
        return this.prefix;
    }
    
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
    @Override
    public void validate(DataNode dataNode) throws ValidationException {
        throw new ValidationException("Validate is not supported for use" +
                                       " node: " + this.getName());
    }
    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor) 
            throws BigDBException {
        return visitor.visit(this);
    }
    
    public Map<String, SchemaNode> getUsedSchemaNodes() {
        return usedSchemaNodes;
    }
    
    @Override
    public UsesSchemaNode clone() {
        throw new RuntimeException("UsesSchemaStatment clone not implemented.");
    }
}
