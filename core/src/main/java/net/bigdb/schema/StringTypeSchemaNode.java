package net.bigdb.schema;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeFactory;
import net.bigdb.data.LeafDataNode;
import net.bigdb.data.memory.MemoryLeafDataNode;
import net.bigdb.yang.TypeStatement;

/**
 * A simple schema node to model the type statement.
 * It contains more detailed specifications of the type
 * e.g, restrictions, enum specifications or union types,
 * etc..
 *
 * It also constructs validators for the node to validate
 * the data.
 *
 * @author kevin.wang@bigswitch.com
 *
 */
public class StringTypeSchemaNode extends TypeSchemaNode {

    public StringTypeSchemaNode() {
        this(null, null);
    }

    public StringTypeSchemaNode(String name, ModuleIdentifier module) {
        super(name, module, LeafType.STRING);
    }

    @Override
    public String getBaseTypePrefix() {
        return baseTypePrefix;
    }

    @Override
    public String getBaseTypeName() {
        return baseTypeName;
    }

    @Override
    public void initTypeInfo(TypeStatement typeStatement)
            throws BigDBException {
        super.initTypeInfo(typeStatement);
        createOrAdjustStringValidator(typeStatement);
    }

    @Override
    public void mergeRestriction(TypeStatement typeStatement)
            throws BigDBException {
        this.createOrAdjustStringValidator(typeStatement);
    }

    @Override
    public LeafDataNode parseDataValueString(DataNodeFactory factory,
            String valueString) throws BigDBException {
        if (factory == null) {
            return new MemoryLeafDataNode(valueString);
        } else {
            return factory.createLeafDataNode(valueString);
        }
    }
    
    @Override
    public void validate(DataNode dataNode) throws BigDBException {
        boolean allowEmptyString = getBooleanAttributeValue(
                SchemaNode.ALLOW_EMPTY_STRING_ATTRIBUTE_NAME, false);
        boolean isEmptyString = (dataNode != null) &&
                dataNode.getString().isEmpty();
        if (!allowEmptyString || !isEmptyString)
            super.validate(dataNode);
    }


    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor)
            throws BigDBException {
        return visitor.visit(this);
    }
}
