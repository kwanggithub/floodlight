package net.bigdb.schema;

import java.util.Set;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.google.common.collect.ImmutableSet;

public abstract class ScalarSchemaNode extends SchemaNode {


    // baseTypedef is the actual type of the node.
    // For a node with derived type, the baseTypedef will be
    // initially assigned an empty place holder,
    // and will be set with the real type after type resolution.
    // For a node with built-in type, baseTpedef will be assigned 
    // its real type after parsing.
    protected TypeSchemaNode baseTypedef;
    
    public ScalarSchemaNode() {
        super(null, null);
    }
    
    public ScalarSchemaNode(String name, ModuleIdentifier module) {
        super(name, module, SchemaNode.NodeType.LEAF);
    }
    
    public ScalarSchemaNode(String name, ModuleIdentifier module,
            LeafType leafType) {
        super(name, module, SchemaNode.NodeType.LEAF);
        this.baseTypedef = TypeSchemaNode.createTypeSchemaNode(leafType,
                                               name, module);
    }
    
    @JsonIgnore
    public TypeSchemaNode getBaseTypedef() {
        return baseTypedef;
    }

    public void setLeafType(LeafType type) {
        this.baseTypedef.setLeafType(type);
    }
    
    public LeafType getLeafType() {
        return this.baseTypedef.getLeafType();
    }
    public void setBaseTypedef(TypeSchemaNode baseType) 
            throws BigDBException {
        this.baseTypedef = baseType;
    }

    public void resolveType(TypeSchemaNode baseType) throws BigDBException {
        TypeSchemaNode typeNode = (TypeSchemaNode)baseType.clone();
        typeNode.resolveType(this.baseTypedef);
        this.baseTypedef = typeNode;
    }
    
    public DataNode getDefaultValue() {
        if (baseTypedef != null)
            return baseTypedef.getDefaultValue();
        return null;
    }
    
    public void setDefaultValue(DataNode defaultValue) 
            throws BigDBException {
        if (defaultValue != null) {
            this.validate(defaultValue);
        }
        baseTypedef.setDefaultValue(defaultValue);
    }
    
    @Override
    public void validate(DataNode dataNode) throws BigDBException {
        if (baseTypedef != null) {
            boolean validate = true;
            if (baseTypedef.getLeafType() == SchemaNode.LeafType.STRING) {
                boolean allowEmptyString = getBooleanAttributeValue(
                        SchemaNode.ALLOW_EMPTY_STRING_ATTRIBUTE_NAME, false);
                boolean isEmptyString = (dataNode != null) &&
                        dataNode.getString().isEmpty();
                if (allowEmptyString && isEmptyString)
                    validate = false;
            }
            if (validate)
                baseTypedef.validate(dataNode);
        } else {
            throw new ValidationException("Type info for node: " + 
                                         this.getName() +
                                         " has not been correctly set.");
        }
    }

    @JsonIgnore
    @Override
    public SchemaNode getChildSchemaNode(String childNodeName)
            throws BigDBException {
        String message = String.format("No child nodes at \"%s\"",
                childNodeName);
        throw new SchemaNodeNotFoundException(childNodeName, message);
    }

    @Override
    public Set<String> getDataSourcesWithDefaultContent() {
        if (baseTypedef.getDefaultValue() != null) {
            String dataSource = dataSources.iterator().next();
            return ImmutableSet.of(dataSource);
        } else {
            return ImmutableSet.of();
        }
    }

    @Override
    public ScalarSchemaNode clone() {
        ScalarSchemaNode schemaNode = (ScalarSchemaNode) super.clone();
        schemaNode.baseTypedef = (TypeSchemaNode) baseTypedef.clone();
        return schemaNode;
    }
}
