package org.projectfloodlight.db.schema;

import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeFactory;
import org.projectfloodlight.db.data.LeafDataNode;
import org.projectfloodlight.db.data.memory.MemoryLeafDataNode;
import org.projectfloodlight.db.yang.EnumStatement;
import org.projectfloodlight.db.yang.TypeStatement;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A simple schema node to model the enumeration type statement.
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
public class EnumTypeSchemaNode extends TypeSchemaNode {

    protected Map<String, EnumStatement> enumerationSpec =
            new HashMap<String, EnumStatement>();

    public EnumTypeSchemaNode() {
        this(null, null);
    }

    public EnumTypeSchemaNode(String name, ModuleIdentifier module) {
        super(name, module, LeafType.ENUMERATION);
    }

    @Override
    public void initTypeInfo(TypeStatement typeStatement)
            throws BigDBException {
        super.initTypeInfo(typeStatement);
        EnumValidator v = this.createEnumValidator(typeStatement);
        this.addValidator(v);
        setEnumerationSpecifications(
                         typeStatement.getEnumSpecification());
    }

    @Override
    public void mergeRestriction(TypeStatement typeStatement)
            throws BigDBException {
        // TODO: add more enum when refining
    }

    @Override
    public LeafDataNode parseDataValueString(DataNodeFactory factory,
            String valueString) throws BigDBException {
        if (valueString != null) {
            Long l = this.enumerationSpec.get(valueString).getValue();
            if (l == null) {
                throw new BigDBException("Invalid enum value: " + valueString);
            }
            // TODO: work around BSC-3061
            // To verify this is correct or fix BSC-3061.
            // It might be ok if we are consistent with the dataNode
            // and always using the string value of enumeration.
            if (factory == null) {
                return new MemoryLeafDataNode(valueString);
            } else {
                return factory.createLeafDataNode(valueString);
            }
        } else {
            throw new BigDBException(
                         "Invalid integer default value: null");
        }
    }

    /**
     * merge information in baseType to this type
     * @param baseType
     * @throws BigDBException
     */
    @Override
    public void resolveType(TypeSchemaNode baseType)
            throws BigDBException {

        DataNode defaultValue = null;
        if (baseType.getDefaultValueString() == null) {
            defaultValue = this.getDefaultValue();
        } else {
            // reset default value
            this.setDefaultValue(null);
            this.setDefaultValueString(baseType.getDefaultValueString());
        }

        if (getDefaultValueString() != null) {
// FIXME: RobV: I think we should basically ignore the integer value assigned
// to an enum constant and just treat everything as strings. Need to fix up the
// enum validator code to conform to this model.
//            try {
//                long l = Long.valueOf(getDefaultValueString());
//                defaultValue = new MemoryLeafDataNode(l);
//            }
//            catch (NumberFormatException exc) {
//                throw new BigDBException(
//                              "Invalid integer default value: " + getDefaultValueString());
//            }
            defaultValue = new MemoryLeafDataNode(getDefaultValueString());
        }
        if (defaultValue != null)
            this.setDefaultValue(defaultValue);
    }

    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor)
            throws BigDBException {
        return visitor.visit(this);
    }

    @Override
    public EnumTypeSchemaNode clone() {
        EnumTypeSchemaNode schemaNode = (EnumTypeSchemaNode) super.clone();
        // Enum statements won't change at this point so it's ok to do shallow
        // copy. Probably the entire enumerationSpec map won't change at this
        // point either, so we probably don't even need to clone that, but we
        // do it just to be safe.
        schemaNode.enumerationSpec = new HashMap<String, EnumStatement>(enumerationSpec);
        return schemaNode;
    }

    @Override
    public EnumValidator createEnumValidator(TypeStatement typeStatement) {
        EnumValidator ev = null;
        for (Validator v : this.getValidator().getValidators()) {
            if (v instanceof EnumValidator) {
                ev = (EnumValidator)v;
                break;
            }
        }
        // FIXME: support refine/augment enum definition?
        if (ev == null) {
            ev = new EnumValidator();
            for (Map.Entry<String, EnumStatement> e :
                typeStatement.getEnumSpecification().entrySet()) {
                ev.addValue(e.getKey(), e.getValue().getValue());
            }
        }
        return ev;
    }

    public void setEnumerationSpecifications(Map<String, EnumStatement> spec) {
        this.enumerationSpec = spec;
    }

    @JsonIgnore
    public Map<String, EnumStatement> getEnumerationSpecifications() {
        return this.enumerationSpec;
    }
}
