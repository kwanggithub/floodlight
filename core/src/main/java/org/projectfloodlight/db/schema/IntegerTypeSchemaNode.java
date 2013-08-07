package org.projectfloodlight.db.schema;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeFactory;
import org.projectfloodlight.db.data.LeafDataNode;
import org.projectfloodlight.db.data.memory.MemoryLeafDataNode;
import org.projectfloodlight.db.yang.TypeStatement;

/**
 * A simple schema node to model the integer type statement.
 * It contains more detailed specifications of the type
 *
 * It also constructs validators for the node to validate
 * the data.
 *
 * @author kevin.wang@bigswitch.com
 *
 */
public class IntegerTypeSchemaNode extends TypeSchemaNode {

    private long max = Long.MAX_VALUE;
    private long min = Long.MIN_VALUE;

    public IntegerTypeSchemaNode() {
        this(null, null);
    }

    public IntegerTypeSchemaNode(String name, ModuleIdentifier module) {
        super(name, module, LeafType.INTEGER);
    }

    @Override
    public void initTypeInfo(TypeStatement typeStatement)
            throws BigDBException {
        super.initTypeInfo(typeStatement);
        String typeName = typeStatement.getName();

        if (typeName.equals("int8"))  {
            min = Byte.MIN_VALUE;
            max = Byte.MAX_VALUE;
        } else if (typeName.equals("int16")) {
            min = Short.MIN_VALUE;
            max = Short.MAX_VALUE;
        } else if (typeName.equals("int32")) {
            min = Integer.MIN_VALUE;
            max = Integer.MAX_VALUE;
        } else if (typeName.equals("int64")) {
            min = Long.MIN_VALUE;
            max = Long.MAX_VALUE;
        } else if (typeName.equals("uint8"))  {
            min = 0;
            max = 0x0ff;
        } else if (typeName.equals("uint16")) {
            min = 0;
            max = 0x0ffff;
        } else if (typeName.equals("uint32")) {
            min = 0;
            max = 0xffffffffL;
        } else if (typeName.equals("uint64")) {
            min = 0;
            // FIXME: Java doesn't have an unsigned long, so we hack it
            // for now and make the max 2^^63-1 instead of 2^^64-1. Need to
            // figure out how to handle this properly, presumably using
            // the BigDecimal class.
            max = Long.MAX_VALUE;
        }

        createOrAdjustRangeValidator(new Range<Long>(min, max),
                                     typeStatement);
    }

    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor)
            throws BigDBException {
        return visitor.visit(this);
    }

    @Override
    public void mergeRestriction(TypeStatement typeStatement)
            throws BigDBException {
        createOrAdjustRangeValidator(null, typeStatement);
    }
    @Override
    public LeafDataNode parseDataValueString(DataNodeFactory factory,
            String valueString) throws BigDBException {
        if (valueString != null) {
            try {
                long l = Long.valueOf(valueString);
                if (factory == null) {
                    return new MemoryLeafDataNode(l);
                } else {
                    return factory.createLeafDataNode(l);
                }
            }
            catch (NumberFormatException exc) {
                throw new BigDBException(
                        "Invalid integer default value: " + valueString);
            }
        } else {
            throw new BigDBException(
                         "Invalid integer default value: null");
        }
    }
}
