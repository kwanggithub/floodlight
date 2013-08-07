package org.projectfloodlight.db.data;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeVisitor.Result;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.SchemaNode;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Iterators;

/** The "Null"DataNode. returned from DataNode.getChild() when no such node exists. Always responds with empty values:
 *  <ul>
 *    <li><code>nullDataNode.getChild(x)</code> returns the NulLDataNode for any value of x
 *    <li><code>nullDataNode.iterator()</li> returns an empty iterator
 *    <li><code>nullDataNode.getLong()</li> returns the default value for long (0L).
 *    <li><code>nullDataNode.getLong(long default)</li> returns the supplied default value
 * </ul>
 *
 * @see DataNode
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
@JsonSerialize(using=NullDataNode.Serializer.class)
public class NullDataNode extends AbstractDataNode implements LeafDataNode {

    public NullDataNode() {
        super();
        safeFreeze();
    }

    @Override
    public DigestValue computeDigestValue() throws BigDBException {
        return DigestValue.fromString(getNodeType().name());
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.NULL;
    }

    @Override
    public String toString() {
        return "NullDataNode";
    }

    @Override
    public Iterator<DataNode> iterator() {
        return Iterators.emptyIterator();
    }

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public DataNode getChild(Step step) throws BigDBException {
        return this;
    }

    @Override
    public Iterable<DictionaryEntry> getDictionaryEntries() throws BigDBException {
        return Collections.<DictionaryEntry>emptySet();
    }

    @Override
    public Iterable<KeyedListEntry> getKeyedListEntries() {
        return Collections.emptyList();
    }

    @Override
    public DataNode getChild(IndexValue name) throws BigDBException {
        return this;
    }

    @Override
    public Set<String> getChildNames() {
        return Collections.<String>emptySet();
    }

    @Override
    public boolean isValueNull() {
        return true;
    }

    @Override
    public boolean getBoolean(boolean def) throws BigDBException {
        return def;
    }

    @Override
    public long getLong(long def) throws BigDBException {
        return def;
    }

    @Override
    public String getString(String def) throws BigDBException {
        return def;
    }

    @Override
    public Object getObject(Object def) throws BigDBException {
        return def;
    }

    @Override
    public BigDecimal getBigDecimal(BigDecimal def) throws BigDBException {
        return def;
    }

    @Override
    public BigInteger getBigInteger(BigInteger def) throws BigDBException {
        return def;
    }

    @Override
    public byte[] getBytes(byte[] def) throws BigDBException {
        return def;
    }

    @Override
    public Result accept(String name, DataNodeVisitor visitor)
            throws BigDBException {
        Result result = visitor.visitNull(name, this);
        return result;
    }

    @Override
    public Result accept(IndexValue keyValue, DataNodeVisitor visitor)
            throws BigDBException {
        Result result = visitor.visitNull(keyValue, this);
        return result;
    }

    static class Serializer extends JsonSerializer<NullDataNode> {

        @Override
        public void serialize(NullDataNode scalarDataNode,
                JsonGenerator generator, SerializerProvider provider)
                throws IOException, JsonProcessingException {
            generator.writeNull();
        }
    }

    @Override
    public double getDouble(double def) throws BigDBException {
        return def;
    }

    @Override
    public LeafType getLeafType() throws BigDBException {
        // TODO Auto-generated method stub
        return null;
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

        return Collections.<DataNodeWithPath>emptyList();
    }
}
