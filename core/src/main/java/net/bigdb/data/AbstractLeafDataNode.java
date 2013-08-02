package net.bigdb.data;

import java.io.IOException;
import java.util.Collections;

import net.bigdb.BigDBException;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.query.Step;
import net.bigdb.schema.SchemaNode;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=AbstractLeafDataNode.Serializer.class)
public abstract class AbstractLeafDataNode extends AbstractDataNode
        implements LeafDataNode {

    @Override
    public NodeType getNodeType() {
        return NodeType.LEAF;
    }

    @Override
    public boolean isScalar() {
        return true;
    }

    @Override
    public DataNodeVisitor.Result accept(String name, DataNodeVisitor visitor)
            throws BigDBException {
        DataNodeVisitor.Result result = visitor.visitLeaf(name, (LeafDataNode) this);
        return result;
    }

    @Override
    public DigestValue computeDigestValue() throws BigDBException {
        Object leafObject = getObject();
        assert leafObject != null;
        return DigestValue.fromString(String.format("%s|%s|%s",
                getNodeType().name(), getLeafType().name(),
                leafObject.toString()));
    }

    @Override
    protected Iterable<DataNodeWithPath> queryWithPath(SchemaNode schemaNode,
            LocationPathExpression queryPath, boolean expandTrailingList,
            boolean includeEmptyContainers) throws BigDBException {
        // The path must always contain the step for this node, so it's an
        // error if it's called with an empty path.
        if (queryPath.size() == 0) {
            throw new BigDBException("Query path argument cannot be empty");
        }

        // We're at a leaf so we should be at the end of the query path
        if (queryPath.size() > 1) {
            throw new BigDBException("Invalid path argument: " + queryPath);
        }

        Step step = queryPath.getStep(0);
        if (!step.getPredicates().isEmpty()) {
            throw new BigDBException(
                    "Leaf nodes cannot have predicates; path: " + queryPath);
        }

        DataNodeWithPath dataNodeWithPath =
                new DataNodeWithPathImpl(queryPath, this);
        return Collections.<DataNodeWithPath>singletonList(dataNodeWithPath);
    }

    static class Serializer extends JsonSerializer<LeafDataNode> {

        @Override
        public void serialize(LeafDataNode leafDataNode,
                JsonGenerator generator, SerializerProvider provider)
                throws IOException, JsonProcessingException {
            try {
                switch (leafDataNode.getLeafType()) {
                case BIG_DECIMAL:
                    generator.writeNumber(leafDataNode.getBigDecimal(null));
                    break;
                case BIG_INTEGER:
                    generator.writeNumber(leafDataNode.getBigInteger(null));
                    break;
                case BINARY:
                    generator.writeBinary(leafDataNode.getBytes(null));
                    break;
                case BOOLEAN:
                    generator.writeBoolean(leafDataNode.getBoolean(false));
                    break;
                case DOUBLE:
                    generator.writeNumber(leafDataNode.getDouble(0.0));
                    break;
                case LONG:
                    generator.writeNumber(leafDataNode.getLong(0));
                    break;
                case STRING:
                    generator.writeString(leafDataNode.getString(null));
                    break;
                default:
                    assert false;
                    break;
                }
            }
            catch (BigDBException e) {
                assert false;
            }
        }
    }
}
