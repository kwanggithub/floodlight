package org.projectfloodlight.db.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeFactory;
import org.projectfloodlight.db.data.LeafDataNode;
import org.projectfloodlight.db.yang.EnumStatement;
import org.projectfloodlight.db.yang.LengthStatement;
import org.projectfloodlight.db.yang.NumericalRestrictions;
import org.projectfloodlight.db.yang.PatternStatement;
import org.projectfloodlight.db.yang.StringRestrictions;
import org.projectfloodlight.db.yang.TypeStatement;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
public class TypeSchemaNode extends SchemaNode {

    private static Map<String, LeafType> nameToType = new HashMap<String, LeafType>();

    static {
        nameToType.put("int8", LeafType.INTEGER);
        nameToType.put("int16", LeafType.INTEGER);
        nameToType.put("int32", LeafType.INTEGER);
        nameToType.put("int64", LeafType.INTEGER);
        nameToType.put("uint8", LeafType.INTEGER);
        nameToType.put("uint16", LeafType.INTEGER);
        nameToType.put("uint32", LeafType.INTEGER);
        nameToType.put("uint64", LeafType.INTEGER);
        nameToType.put("enumeration", LeafType.ENUMERATION);
        nameToType.put("string", LeafType.STRING);
        nameToType.put("boolean", LeafType.BOOLEAN);
        nameToType.put("union", LeafType.UNION);
    }

    private static LeafType leafType(String name) {
        return nameToType.get(name);
    }

    // While loading the schema we keep track of the prefix/name
    // but don't resolve to a TypedefSchemaNode until first access.
    // This is to support forward references to typedefs during
    // the module loading process.
    protected String baseTypeName;
    protected String baseTypePrefix;

    private LeafType leafType;
    private CompoundValidator validator = new CompoundValidator();

    // for default value
    // default value can be specified at different places
    // leaf node or typedef node.
    // the nearest value took effects when it is resolved.
    // FIXME: Ideally we'd initialize this to DataNode.NULL to be consistent
    // with how the rest of the code deals with "null" data nodes. But if we
    // do that it's not obvious how to get Jackson to avoid emitting that
    // property during serialization of the schema info when it's "null".
    // So for now we just leave it as a real null value.
    private DataNode defaultValue;
    private String defaultValueString;

    private TypeStatement ownTypeStatement;

    public TypeSchemaNode() {
        this(null, null);
    }

    public TypeSchemaNode(String name, ModuleIdentifier module) {
        this(name, module, LeafType.NEED_RESOLVE);
    }

    public TypeSchemaNode(String name, ModuleIdentifier module, LeafType type) {
        super(name, module, NodeType.TYPE);
        this.leafType = type;
    }

    public static TypeSchemaNode
        createTypeSchemaNode(LeafType type, String nodeName, ModuleIdentifier module) {
        if (type == null) {
            return new TypeSchemaNode(nodeName, module);
        } else if (type == LeafType.INTEGER) {
            return new IntegerTypeSchemaNode(nodeName, module);
        } else if (type == LeafType.ENUMERATION) {
            return new EnumTypeSchemaNode(nodeName, module);
        } else if (type == LeafType.STRING) {
            return new StringTypeSchemaNode(nodeName, module);
        } else if (type == LeafType.BOOLEAN) {
            return new BooleanTypeSchemaNode(nodeName, module);
        } else if (type == LeafType.UNION) {
            return new UnionTypeSchemaNode(nodeName, module);
        } else {
            return new TypeSchemaNode(nodeName, module);
        }
    }
    public static TypeSchemaNode
        createTypeSchemaNode(String typeName, String prefix,
                             String nodeName,
                             ModuleIdentifier module) {
      TypeSchemaNode typeNode = null;
      LeafType type = leafType(typeName);
      typeNode = createTypeSchemaNode(type, nodeName, module);
      if (type == null) {
          typeNode.setBaseType(prefix, typeName);
      }
      return typeNode;
    }

    public List<Validator> getTypeValidator() {
        List<Validator> vs = new ArrayList<Validator>();
        for (Validator v : this.getValidator().getValidators()) {
            if (v instanceof StringValidator) {
                StringValidator sv = (StringValidator)v;
                vs.add(sv.getLengthValidator());
                vs.addAll(sv.getPatternValidators());
            } else {
                vs.add(v);
            }
        }
        return vs;
    }

    public String getBaseTypePrefix() {
        return baseTypePrefix;
    }

    public String getBaseTypeName() {
        return baseTypeName;
    }

    public void initTypeInfo(TypeStatement typeStatement)
            throws BigDBException {
        this.setOwnTypeStatement(typeStatement);
    }

    public LeafDataNode parseValueWithValidation(DataNodeFactory factory,
            String valueString)
        throws BigDBException {
        LeafDataNode d = this.parseDataValueString(factory, valueString);
        this.validate(d);
        return d;
    }
    
    public LeafDataNode parseDataValueString(DataNodeFactory factory,
            String valueString) throws BigDBException {
        throw new BigDBException(
                       "Cannot parse value with TypeSchemaNode: " +
                               valueString);
    }

    public LeafDataNode parseDataValueString(String valueString)
            throws BigDBException {
        return parseDataValueString(null, valueString);
    }

    public void mergeRestriction(TypeStatement typeStatement)
            throws BigDBException {
        // do nothing
    }

    /**
     * merge information in baseType to this type
     * @param baseType
     * @throws BigDBException
     */
    public void resolveType(TypeSchemaNode baseType)
            throws BigDBException {

        // FIXME: This code is confusing and should be cleaned up.
        // The local variable hides the data member
        DataNode defaultValue = null;
        if (baseType.getDefaultValueString() == null) {
            defaultValue = this.getDefaultValue();
            this.setDefaultValue(null);
        } else {
            // reset default value
            this.setDefaultValue(null);
            this.setDefaultValueString(baseType.getDefaultValueString());
        }
        this.mergeRestriction(baseType.getOwnTypeStatement());
        if (this.getDefaultValueString() != null && defaultValue == null) {
            defaultValue = parseDataValueString(this.getDefaultValueString());
        }

        if (defaultValue != null)
            this.setDefaultValue(defaultValue);
    }

    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor)
            throws BigDBException {
        return visitor.visit(this);
    }

    public void setBaseType(String prefix, String name) {
        this.baseTypePrefix = prefix;
        this.baseTypeName = name;
    }

    @Override
    public TypeSchemaNode clone() {
        TypeSchemaNode typeSchemaNode = (TypeSchemaNode) super.clone();
        typeSchemaNode.validator = validator.clone();
        return typeSchemaNode;
    }

    public void addValidator(Validator validator) {
        if (this.getValidator() == null)
            this.setValidator(new CompoundValidator());
        this.getValidator().add(validator);
    }

    public void removeValidator(Validator validator) {
        if (this.getValidator() != null)
            this.getValidator().remove(validator);
    }

    /**
     * Create a new StringValidator based on the current
     * StringValidator if there is one.
     * It checks and create new length restriction and
     * add the new pattern restriction.
     *
     * @param typeStatement
     * @return
     * @throws BigDBException
     */
    public StringValidator
    createOrAdjustStringValidator(TypeStatement typeStatement)
        throws BigDBException {

        StringValidator sv = null;
        for (Validator v : this.getValidator().getValidators()) {
            if (v instanceof StringValidator) {
                sv = (StringValidator)v;
                break;
            }
        }
        if (typeStatement != null &&
            typeStatement.getStringRestrictions() != null) {
            // add new restrictions to string validator
            StringRestrictions r = typeStatement.getStringRestrictions();
            if (r.getPatternStatements() != null) {
                for (PatternStatement p : r.getPatternStatements()) {
                    if (sv == null) {
                        sv = new StringValidator();
                        this.addValidator(sv);
                    }
                    sv.addPattern(p.getPattern());
                }
            }
            LengthStatement ls = r.getLengthStatement();
            if (ls != null) {
                if (sv == null) {
                    sv = new StringValidator();
                    this.addValidator(sv);
                }
                sv.add(ls.getLengthParts());
            }
        }
        return sv;
    }

    @Override
    public void validate(DataNode dataNode) throws BigDBException {
        getValidator().validate(dataNode);
    }

    /**
     * This function adds the numeric restrictions to the valudator of the node.
     * It searches the validator list, if there is already a range validator,
     * it adds the new range to the existing validator. if there is no existing
     * range validator, it creates a new one and add to the validator list.
     *
     * @param minMax
     * @param typeStatement
     * @return
     * @throws BigDBException
     */
    @SuppressWarnings("unchecked")
    public RangeValidator<Long>
        createOrAdjustRangeValidator(Range<Long> minMax, TypeStatement typeStatement)
           throws BigDBException {
        RangeValidator<Long> rv = null;
        for (Validator v : this.getValidator().getValidators()) {
            if (v instanceof RangeValidator) {
                rv = (RangeValidator<Long>)v;
                break;
            }
        }
        if (rv == null) {
            // there is no range validator, meaning we are initializing a
            // a type schema type, we need to add the range to the type
            // node.
            assert minMax != null;
            rv = new RangeValidator<Long>(minMax.getStart(), minMax.getEnd());
            this.addValidator(rv);
        }
        if (typeStatement != null) {
            NumericalRestrictions nr = typeStatement.getNumericalRestrictions();
            if (nr != null && nr.getRangeStatement() != null) {
                rv.checkAndSet(nr.getRangeStatement().getRangeParts());
            }
        }
        return rv;
    }

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

    @JsonIgnore
    public TypeStatement getOwnTypeStatement() {
        return ownTypeStatement;
    }

    public void setOwnTypeStatement(TypeStatement ownTypeStatement) {
        this.ownTypeStatement = ownTypeStatement;
    }

    public String getDefaultValueString() {
        return defaultValueString;
    }

    public void setDefaultValueString(String defaultValueString) {
        this.defaultValueString = defaultValueString;
    }

    @JsonIgnore
    public DataNode getDefaultValue() {
        return defaultValue;
    }


    public void setDefaultValue(DataNode defaultValue) {
        this.defaultValue = defaultValue;
    }

    public LeafType getLeafType() {
        return leafType;
    }

    public void setLeafType(LeafType leafType) {
        this.leafType = leafType;
    }

    @JsonIgnore
    public CompoundValidator getValidator() {
        return validator;
    }

    public void setValidator(CompoundValidator validator) {
        this.validator = validator;
    }

}
