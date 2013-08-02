package net.bigdb.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;
import net.bigdb.data.DataNode.NodeType;
import net.bigdb.data.IndexSpecifier.SortOrder;
import net.bigdb.data.memory.MemoryLeafDataNode;
import net.bigdb.data.memory.MemoryListElementDataNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IndexValue implements Comparable<IndexValue> {

    public static final class Builder {

        private final IndexSpecifier indexSpecifier;
        private final Map<String, DataNode> values = new HashMap<String, DataNode>();

        public Builder(IndexSpecifier indexSpecifier) {
            this.indexSpecifier = indexSpecifier;
        }

        public Builder addValue(String name, DataNode value) {
            values.put(name, value);
            return this;
        }

        public IndexValue getIndexValue() throws BigDBException {
            return IndexValue.fromValues(indexSpecifier, values);
        }
    }

    protected final static Logger logger = LoggerFactory.getLogger(IndexValue.class);

    private final IndexSpecifier indexSpecifier;
    private final DataNode elementDataNode;

    private IndexValue(IndexSpecifier indexSpecifier, DataNode elementDataNode) {
        assert !elementDataNode.isNull();
        this.indexSpecifier = indexSpecifier;
        this.elementDataNode = elementDataNode;
    }

    private IndexValue(IndexSpecifier indexSpecifier, Map<String,
            DataNode> values) throws BigDBException {
        this.indexSpecifier = indexSpecifier;
        this.elementDataNode = new MemoryListElementDataNode(false, values);
    }

    public static IndexValue fromListElement(IndexSpecifier indexSpecifier,
            DataNode listElementDataNode) {

        assert listElementDataNode.getNodeType() == NodeType.LIST_ELEMENT;

        return new IndexValue(indexSpecifier, listElementDataNode);
    }

    public static IndexValue fromValues(IndexSpecifier indexSpecifier,
            Map<String, DataNode> values) throws BigDBException {
        for(DataNode node: values.values()) {
            assert !node.isNull();
            assert node.isScalar();
        }
        return new IndexValue(indexSpecifier, values);
    }

    /**
     * Construct an index value from a key name & string value.
     * This only works for lists that have a single key field that's a string.
     *
     * @param keyName the name of the key field
     * @param keyValue the string value of the key
     * @return
     * @throws BigDBException
     */
    public static IndexValue fromStringKey(String keyName, String keyValue)
            throws BigDBException {
        IndexSpecifier keySpecifier = IndexSpecifier.fromFieldNames(keyName);
        DataNode keyDataNode = new MemoryLeafDataNode(keyValue);
        Map<String, DataNode> keyMap = Collections.singletonMap(keyName, keyDataNode);
        return IndexValue.fromValues(keySpecifier, keyMap);
    }

    /**
     * Construct an index value from a key name & long value.
     * This only works for lists that have a single key field that's a long.
     *
     * @param keyName the name of the key field
     * @param keyValue the long value of the key
     * @return
     * @throws BigDBException
     */
    public static IndexValue fromLongKey(String keyName, long keyValue)
            throws BigDBException {
        IndexSpecifier keySpecifier = IndexSpecifier.fromFieldNames(keyName);
        DataNode keyDataNode = new MemoryLeafDataNode(keyValue);
        Map<String, DataNode> keyMap = Collections.singletonMap(keyName, keyDataNode);
        return IndexValue.fromValues(keySpecifier, keyMap);
    }

    public IndexSpecifier getIndexSpecifier() {
        return indexSpecifier;
    }

    public DataNode getDataNode() {
        return elementDataNode;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public int compareTo(IndexValue indexValue) {
        int result = 0;
        try {
            if ((indexValue == null) || !indexSpecifier.equals(indexValue.indexSpecifier)) {
                throw new BigDBException("Invalid IndexValue.compareTo call");
            }
            for (IndexSpecifier.Field field: indexSpecifier.getFields()) {
                String name = field.getName();
                DataNode dataNode1 = elementDataNode.getChild(name);
                Comparable comparable1 = (Comparable<?>) dataNode1.getObject();
                DataNode dataNode2 =  indexValue.elementDataNode.getChild(name);
                Comparable comparable2 = (Comparable<?>) dataNode2.getObject();

                // If the field is a case-insensitive compare of strings, then
                // convert the values to lower case before doing the comparison.
                if ((comparable1 != null) && (comparable2 != null) &&
                        (comparable1 instanceof String) && !field.isCaseSensitive()) {
                    comparable1 = ((String)comparable1).toLowerCase();
                    comparable2 = ((String)comparable2).toLowerCase();
                }
                if (comparable1 != null) {
                    if (comparable2 != null)
                        result = comparable1.compareTo(comparable2);
                    else
                        result = 1;
                } else if (comparable2 != null) {
                    result = -1;
                } else {
                    result = 0;
                }

                if (result != 0) {
                    if (field.getSortOrder() == SortOrder.REVERSE)
                        result = -result;
                    break;
                }
            }
        }
        catch (BigDBException e) {
            // Should have already verified that the values for the indexed
            // fields exist in the element node, so this shouldn't happen.
            logger.error("Invalid index value; " + e);
            throw new BigDBInternalError("Error comparing index values", e);
        }

        return result;
    }

    @Override
    public String toString() {
        try {
            StringBuilder builder = new StringBuilder();
            boolean firstTime = true;
            for (IndexSpecifier.Field field: indexSpecifier.getFields()) {
                if (firstTime)
                    firstTime = false;
                else
                    builder.append('|');
                DataNode dataNode = elementDataNode.getChild(field.getName());
                builder.append(dataNode.getString());
            }
            return builder.toString();
        }
        catch (BigDBException e) {
            return "<invalid-key-value>";
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result +
                ((indexSpecifier == null) ? 0 : indexSpecifier.hashCode());
        if (indexSpecifier != null) {
            try {
                for (IndexSpecifier.Field field: indexSpecifier.getFields()) {
                    String name = field.getName();
                    DataNode dataNode = elementDataNode.getChild(name);
                    Object obj = dataNode.getObject();
                    int value = 0;
                    if (obj != null) {
                        if ((obj instanceof String) && !field.isCaseSensitive()) {
                            obj = ((String)obj).toLowerCase();
                        }
                        value = obj.hashCode();
                    }
                    result = prime * result + value;
                }
            }
            catch (BigDBException e) {
                throw new BigDBInternalError(
                        "Index value does not specify all field values", e);
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        return compareTo((IndexValue) obj) == 0;
    }
}
