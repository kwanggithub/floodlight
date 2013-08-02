package net.bigdb.schema;

import net.bigdb.BigDBException;
import net.bigdb.yang.TypeStatement;

/**
 * A simple schema node to model the leaf ref type statement.
 * 
 * FIXME: RobV: I think this is only partially implemented, so I don't think
 * any of this code has actually been tested.
 *
 * @author kevin.wang@bigswitch.com
 *
 */
public class LeafrefTypeSchemaNode extends TypeSchemaNode {
    
// RobV: Commenting these out for now since we're not using them yet to get
// rid of warnings.
//    private String xpath;
//    private SchemaNode directlyRefNode;
//    private SchemaNode actualNode;
    
    public LeafrefTypeSchemaNode() {
        this(null, null);
    }
    
    public LeafrefTypeSchemaNode(String name, ModuleIdentifier module) {
        super(name, module, LeafType.LEAF_REF);
    }

    public void initTypeInfo(TypeStatement typeStatement) 
            throws BigDBException {
        super.initTypeInfo(typeStatement);
        
        //TODO: set up the xpath
    }
    
    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor) 
            throws BigDBException {
        return visitor.visit(this);
    }
    
    @Override
    public LeafrefTypeSchemaNode clone() {
        LeafrefTypeSchemaNode schemaNode = (LeafrefTypeSchemaNode) super.clone();
        // FIXME: Do we need to clone either of the schema nodes?
        return schemaNode;
    }
}
