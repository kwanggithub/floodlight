package net.bigdb.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeFactory;
import net.bigdb.data.LeafDataNode;
import net.bigdb.yang.TypeStatement;

/**
 * A simple schema node to model the union type statement.
 *
 * It also constructs validators for the node to validate
 * the data.
 *
 * @author kevin.wang@bigswitch.com
 *
 */
public class UnionTypeSchemaNode extends TypeSchemaNode {

    private List<TypeSchemaNode> unionTypes =
            new ArrayList<TypeSchemaNode>();

    public UnionTypeSchemaNode() {
        this(null, null);
    }

    public UnionTypeSchemaNode(String name, ModuleIdentifier module) {
        super(name, module, LeafType.UNION);
    }

    @Override
    public void initTypeInfo(TypeStatement typeStatement)
            throws BigDBException {
        super.initTypeInfo(typeStatement);
        // union subtypes are added one by one when creating this node.
    }

    @Override
    public void mergeRestriction(TypeStatement typeStatement)
            throws BigDBException {
    }

    @Override
    public LeafDataNode parseValueWithValidation(DataNodeFactory factory,
            String valueString) throws BigDBException {

        return this.parseDataValueString(factory, valueString);

    }
    @Override
    public LeafDataNode parseDataValueString(DataNodeFactory factory,
            String valueString) throws BigDBException {
        for (TypeSchemaNode type : this.unionTypes) {
            try {
                LeafDataNode d = type.parseValueWithValidation(factory, valueString);
                return d;
            } catch (BigDBException e) {
            }
        }
        throw new BigDBException("Data does not match any type of the union: " +
                                 valueString);
    }

    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor)
            throws BigDBException {
        return visitor.visit(this);
    }


    @Override
    public UnionTypeSchemaNode clone() {
        UnionTypeSchemaNode schemaNode = (UnionTypeSchemaNode) super.clone();
        schemaNode.unionTypes = new ArrayList<TypeSchemaNode>();
        for (TypeSchemaNode typeSchemaNode: unionTypes) {
            schemaNode.unionTypes.add(typeSchemaNode.clone());
        }
        return schemaNode;
    }

    @Override
    public void validate(DataNode dataNode) throws BigDBException {
        boolean ok = false;
        for (TypeSchemaNode ts : this.getTypeSchemaNodes()) {
            try {
                ts.validate(dataNode);
                ok = true;
                break;
            } catch (ValidationException e) {
            }
        }
        if (!ok) {
            throw new ValidationException("Data validation failed " +
                    getName());
        }
    }

    public void addTypeSchemanNode(TypeSchemaNode type) {
        unionTypes.add(type);
    }

    public List<TypeSchemaNode> getTypeSchemaNodes() {
        return Collections.unmodifiableList(this.unionTypes);
    }

    public void setTypeSchemaNodes(List<TypeSchemaNode> types) {
        this.unionTypes = types;
    }
}
