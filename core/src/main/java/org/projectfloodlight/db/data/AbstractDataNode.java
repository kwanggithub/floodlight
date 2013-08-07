package org.projectfloodlight.db.data;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Set;

import javax.annotation.CheckForNull;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.SchemaNode;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.common.collect.UnmodifiableIterator;

public class AbstractDataNode implements DataNode {

    /**
     * Simple implementation of the DataNodeWithPath interface that's used to
     * return query results.
     */
    public static final class DataNodeWithPathImpl implements DataNode.DataNodeWithPath {

        private final LocationPathExpression path;
        private final DataNode dataNode;

        public DataNodeWithPathImpl(LocationPathExpression path, DataNode dataNode) {
            this.path = path;
            this.dataNode = dataNode;
        }

        @Override
        public LocationPathExpression getPath() {
            return path;
        }

        @Override
        public DataNode getDataNode() {
            return dataNode;
        }

        @Override
        public String toString() {
            return String.format("{%npath: \"%s\",%nnode: %s%n}", path, dataNode);
        }
    }

    /**
     * Iterator<DataNode> implementation that wraps a Iterator<DataNodeWithPath>
     * and strips the path and just returns the data node from each result from
     * the underlying iterator.
     *
     * @author rob.vaterlaus@bigswitch.com
     */
    public static final class DataNodePathStrippingIterator extends
            UnmodifiableIterator<DataNode> {

        private Iterator<DataNodeWithPath> iterator;

        public DataNodePathStrippingIterator(Iterator<DataNodeWithPath> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public DataNode next() {
            DataNodeWithPath dataNodeWithPath = iterator.next();
            return dataNodeWithPath.getDataNode();
        }
    }

    /**
     * Iterable<DataNode> implementation that wraps an
     * Iterable<DataNodeWithPath> and strips the path and just returns the data
     * node from each result from the underlying iterable.
     *
     * @author rob.vaterlaus@bigswitch.com
     */
    public static final class DataNodePathStrippingIterable implements
            Iterable<DataNode> {

        private Iterable<DataNodeWithPath> iterable;

        public DataNodePathStrippingIterable(Iterable<DataNodeWithPath> iterable) {
            this.iterable = iterable;
        }

        @Override
        public Iterator<DataNode> iterator() {
            return new DataNodePathStrippingIterator(iterable.iterator());
        }
    }

    private boolean mutable = true;
    private volatile DigestValue digestValue = null;

    @Override
    public boolean isMutable() {
        return mutable;
    }

    protected void checkMutable() throws BigDBException {
        if (!isMutable()) {
            throw new BigDBException("Data node is not mutable");
        }
    }

    @Override
    public String toString() {
        String text = DataNodeUtilities.dataNodeToString(this);
        return text;
    }

    @Override
    public NodeType getNodeType() {
        throw new UnsupportedOperationException("DataNode.getNodeType must be overridden");
    }

    /**
     * Default implementation of computeDigestValue doesn't compute a valid
     * digest value. This is typically overridden by subclasses to compute a
     * real digest value based on the contents of the data node. If a subclass
     * doesn't override this and an instance of that class is used in a context
     * where the digest is required (e.g. a diff data node) then an exception
     * is thrown when that code attempts to get the digest value.
     * @return
     * @throws BigDBException
     */
    protected DigestValue computeDigestValue() throws BigDBException {
        return null;
    }

    @Override
    public DigestValue getDigestValue() throws BigDBException {
        if (mutable)
            throw new BigDBException("getDigestValue only allowed for immutable data nodes");
        // Use double-check locking to compute the digest value
        DigestValue result = digestValue;
        if (result == null) {
            synchronized(this) {
                result = digestValue;
                if (result == null) {
                    result = digestValue = computeDigestValue();
                    if (result == null)
                        throw new BigDBException("Digest values not supported by the data node implementation");
                }
            }
        }
        return digestValue;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isScalar() {
        return false;
    }

    @Override
    public boolean isIterable() {
        return false;
    }

    @Override
    public boolean isDictionary() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isKeyedList() {
        return false;
    }

    @Override
    public boolean isValueNull() {
        return false;
    }

    @Override
    public IndexSpecifier getKeySpecifier() {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getKeySpecifier()");
    }

    @Override
    public int childCount() throws BigDBException {
        return 0;
    }

    @Override
    public boolean hasChildren() throws BigDBException {
        return false;
    }

    @Override
    public boolean hasChild(String name) throws BigDBException {
        return false;
    }

    @Override
    public DataNode getChild(int index) {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getChild(int index)");
    }

    @Override
    public Iterator<DataNode> iterator() {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation iterator");
    }

    @Override
    public DataNode getChild(String name) throws BigDBException {
        return getChild(Step.of(name));
    }

    @Override
    public DataNode getChild(Step step) throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support getChild by name");
    }

    @Override
    public Set<String> getChildNames() throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getChildNames()");
    }

    @Override
    public Iterable<DictionaryEntry> getDictionaryEntries() throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getChildEntries()");
    }

    @Override
    public Iterable<KeyedListEntry> getKeyedListEntries() throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getKeyedListEntries()");
    }

    @Override
    public boolean hasChild(IndexValue name) throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation hasChild(IndexValue)");
    }

    @Override
    public DataNode getChild(IndexValue name) throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getChild(IndexValue)");
    }

    @Override
    public boolean getBoolean(boolean def) throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getBoolean()");
    }

    @Override
    public long getLong(long def) throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getLong()");
    }

    @Override
    public double getDouble(double def) throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getDouble()");
    }

    @Override
    public String getString(String def) throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getString()");
    }

    @Override
    public Object getObject(Object def) throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getObject()");
    }

    @Override
    public BigDecimal getBigDecimal(BigDecimal def) throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getBigDecimal()");
    }

    @Override
    public BigInteger getBigInteger(BigInteger def) throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getBigInteger()");
    }
    @Override
    public byte[] getBytes(byte[] def) throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " + getClass() +
                " does not support operation getBytes()");
    }

    @Override
    public boolean getBoolean() throws BigDBException {
        return getBoolean(DataNode.DEFAULT_BOOLEAN);
    }

    @Override
    public long getLong() throws BigDBException {
        return getLong(DataNode.DEFAULT_LONG);
    }

    @Override
    public double getDouble() throws BigDBException {
        return getDouble(DataNode.DEFAULT_DOUBLE);
    }

    @Override
    public String getString() throws BigDBException {
        return getString(DataNode.DEFAULT_STRING);
    }

    @Override
    @CheckForNull
    public Object getObject() throws BigDBException {
        return getObject(DataNode.DEFAULT_OBJECT);
    }

    @Override
    public BigDecimal getBigDecimal() throws BigDBException {
        return getBigDecimal(DataNode.DEFAULT_BIG_DECIMAL);
    }

    @Override
    public BigInteger getBigInteger() throws BigDBException {
        return getBigInteger(DataNode.DEFAULT_BIG_INTEGER);
    }

    @Override
    public byte[] getBytes() throws BigDBException {
        return getBytes(DataNode.DEFAULT_BYTES);
    }

    @Override
    public Iterable<DataNode> query(SchemaNode schemaNode,
            LocationPathExpression queryPath) throws BigDBException {
        Iterable<DataNodeWithPath> result =
                queryWithPath(schemaNode, queryPath, true);
        return new DataNodePathStrippingIterable(result);
    }

    /**
     * Query for the child nodes of the current data node that match the input
     * query path.
     *
     * @param schemaNode
     *            the schema node corresponding to the data node being queried.
     * @param queryPath
     *            the path specifying the nodes to be queried. The first step in
     *            the path should correspond to the data node being queried.
     *            This allows you to query for a subset of list element nodes of
     *            a list node by specifying a predicate in the first step.
     * @param expandTrailingList
     *            if this is true and the last step in the query path
     *            corresponds to a list data node, then the query results are
     *            the list elements of that list node that match the predicate
     *            for the final step. If there is no predicate then all of the
     *            list elements are returned. If expandTrailingList is false,
     *            then the list element itself is returned rather than expanding
     *            out to the matching list elements. In that case any predicate
     *            in the query path is ignored.
     * @param includeEmptyContainers
     *            if true then include empty containers in the results; if false
     *            then omit them
     * @return the descendant data nodes matching the specified query path.
     *         Include the path of each matching nodes in the result.
     * @throws BigDBException
     */
    protected Iterable<DataNodeWithPath> queryWithPath(SchemaNode schemaNode,
            LocationPathExpression queryPath, boolean expandTrailingList,
            boolean includeEmptyContainers) throws BigDBException {
        throw new UnsupportedOperationException("DataNode of type " +
                getClass() + " does not support operation queryWithPath()");
    }

    @Override
    public Iterable<DataNodeWithPath> queryWithPath(SchemaNode schemaNode,
            LocationPathExpression queryPath, boolean expandTrailingList)
            throws BigDBException {
        return queryWithPath(schemaNode, queryPath, expandTrailingList, true);
    }

    @Override
    public Iterable<DataNodeWithPath> queryWithPath(SchemaNode schemaNode,
            LocationPathExpression queryPath) throws BigDBException {
        return queryWithPath(schemaNode, queryPath, true);
    }

    /**
     * Make the data node immutable.
     * @throws BigDBException
     */
    protected void freeze() throws BigDBException {
        this.mutable = false;
    }

    /**
     * This can be called if it's known that the freeze operation cannot fail.
     * Don't call this unless you really know what you're doing.
     */
    protected void safeFreeze() {
        try {
            freeze();
        }
        catch (BigDBException e) {
            throw new BigDBInternalError(
                    "Unexpected exception while freezing data node", e);
        }
    }

    @Override
    public DataNodeVisitor.Result accept(String name, DataNodeVisitor visitor)
            throws BigDBException {
        throw new UnsupportedOperationException("DataNode.accept(" +
                "String name, DataNodeVisitor visitor) not supported");
    }

    @Override
    public DataNodeVisitor.Result accept(IndexValue keyValue, DataNodeVisitor visitor)
            throws BigDBException {
        throw new UnsupportedOperationException("DataNode.accept(" +
                "IndexValue keyValue, DataNodeVisitor visitor) not supported");
    }

    static class ArraySerializer extends JsonSerializer<DataNode> {

        @Override
        public void serialize(DataNode dataNode, JsonGenerator generator,
                SerializerProvider provider) throws IOException,
                JsonProcessingException {
            generator.writeStartArray();
            for (DataNode childNode: dataNode) {
                generator.writeObject(childNode);
            }
            generator.writeEndArray();
        }
    }

    static class DictionarySerializer extends JsonSerializer<DataNode> {

        @Override
        public void serialize(DataNode dataNode,
                JsonGenerator generator, SerializerProvider provider)
                throws IOException, JsonProcessingException {
            try {
                generator.writeStartObject();
                for (DictionaryEntry entry: dataNode.getDictionaryEntries()) {
                    String childName = entry.getName();
                    DataNode childNode = entry.getDataNode();
                    generator.writeObjectField(childName, childNode);
                }
                generator.writeEndObject();
            }
            catch (BigDBException e) {
                assert false;
            }
        }
    }
}
