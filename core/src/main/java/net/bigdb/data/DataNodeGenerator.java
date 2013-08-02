package net.bigdb.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Stack;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode.NodeType;
import net.bigdb.data.memory.MemoryDataNodeFactory;
import net.bigdb.schema.LeafListSchemaNode;
import net.bigdb.schema.LeafSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.SchemaNode;
import net.bigdb.schema.TypeSchemaNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataNodeGenerator {

    public enum Inclusion {
        ALWAYS,
        NON_NULL,
        NON_EMPTY
    };

    protected final static Logger logger =
            LoggerFactory.getLogger(DataNodeGenerator.class);

    private final SchemaNode rootSchemaNode;
    private final DataNodeFactory dataNodeFactory;
    private final Stack<SchemaNode> schemaNodeStack = new Stack<SchemaNode>();
    private final Stack<DataNode> dataNodeStack = new Stack<DataNode>();
    private final Stack<String> fieldNameStack = new Stack<String>();
    private String pendingFieldName;
    private boolean pendingFieldOptional;
    private DataNode result = DataNode.NULL;
    private Inclusion inclusion = Inclusion.NON_EMPTY;

    public DataNodeGenerator(SchemaNode rootSchemaNode) throws BigDBException {
        this(rootSchemaNode, new MemoryDataNodeFactory());
    }

    public DataNodeGenerator(SchemaNode rootSchemaNode,
            DataNodeFactory dataNodeFactory) throws BigDBException {
        assert rootSchemaNode != null;
        assert dataNodeFactory != null;
        this.rootSchemaNode = rootSchemaNode;
        this.dataNodeFactory = dataNodeFactory;
    }

    public void setInclusion(Inclusion inclusion) {
        this.inclusion = inclusion;
    }

    public DataNode getResult() throws BigDBException {
        return result;
    }

    public DataNode.NodeType getCurrentNodeType() {
        return dataNodeStack.isEmpty() ? null :
                dataNodeStack.peek().getNodeType();
    }

    private void setResult(DataNode dataNode) throws BigDBException {
        if (logger.isTraceEnabled()) {
            logger.trace("Setting result to:\n" + dataNode.toString());
        }
        if (!result.isNull()) {
            throw new BigDBException(
                    "Unexpected overwrite of previous data result");
        }
        result = dataNode;
    }

    private void pushSchemaNodeStack() throws BigDBException {
        if (schemaNodeStack.empty()) {
            assert pendingFieldName == null;
            schemaNodeStack.push(rootSchemaNode);
        } else {
            SchemaNode child = null;
            SchemaNode current = schemaNodeStack.peek();
            if (current != null) {
                switch (current.getNodeType()) {
                case CONTAINER:
                case LIST_ELEMENT:
                    assert pendingFieldName != null;
                    assert !pendingFieldName.isEmpty();
                    child = current.getChildSchemaNode(pendingFieldName,
                            !pendingFieldOptional);
                    break;
                case LEAF_LIST:
                    child = ((LeafListSchemaNode)current).getLeafSchemaNode();
                    break;
                case LIST:
                    child = ((ListSchemaNode)current).getListElementSchemaNode();
                    break;
                default:
                    assert false;
                }
            }
            schemaNodeStack.push(child);
        }
    }

    private void popSchemaNodeStack() {
        schemaNodeStack.pop();
    }

    private void writeDataNode(DataNode dataNode)
            throws BigDBException {
        SchemaNode schemaNode = schemaNodeStack.peek();
        if (schemaNode != null) {
            if (dataNodeStack.isEmpty()) {
                if (pendingFieldName != null)
                    throw new BigDBException("Root data node must be unnamed");
                switch (dataNode.getNodeType()) {
                case LEAF:
                case NULL:
                    setResult(dataNode);
                    break;
                default:
                    dataNodeStack.push(dataNode);
                    break;
                }
            } else {
                DataNode parentDataNode = dataNodeStack.peek();
                NodeType parentNodeType = parentDataNode.getNodeType();

                switch (dataNode.getNodeType()) {
                case NULL:
                    if (inclusion == Inclusion.ALWAYS) {
                        if (!dataNode.isDictionary())
                            throw new DataNodeTypeMismatchException();
                        DictionaryDataNode dictionaryDataNode =
                                (DictionaryDataNode) parentDataNode;
                        dictionaryDataNode.put(pendingFieldName, dataNode);
                    }
                    break;
                case CONTAINER:
                case LEAF_LIST:
                case LIST:
                    if (pendingFieldName == null)
                        throw new BigDBException("Invalid unnamed node");
                    fieldNameStack.push(pendingFieldName);
                    dataNodeStack.push(dataNode);
                    break;
                case LIST_ELEMENT:
                    dataNodeStack.push(dataNode);
                    break;
                case LEAF:
                    switch (parentNodeType) {
                    case LEAF_LIST:
                        ((LeafListDataNode) parentDataNode).add(dataNode);
                        break;
                    case CONTAINER:
                    case LIST_ELEMENT:
                        if (pendingFieldName == null) {
                            throw new BigDBException("Child nodes of " +
                                    "container and list elements nodes must " +
                                    "have a field name");
                        }
                        DictionaryDataNode dictionaryDataNode =
                                (DictionaryDataNode) parentDataNode;
                        dictionaryDataNode.put(pendingFieldName, dataNode);
                        break;
                    default:
                        throw new DataNodeTypeMismatchException("Parent node " +
                                "type of a leaf node must be either a leaf-list, " +
                                "container, or list element");
                    }
                    break;
                }
            }
        }

        pendingFieldName = null;
    }

    private void writeDataNodeEnd() throws BigDBException {
        SchemaNode schemaNode = schemaNodeStack.peek();
        if (schemaNode == null)
            return;

        if (dataNodeStack.isEmpty()) {
            throw new BigDBException(
                    "Data node end called without matching start");
        }

        DataNode dataNode = dataNodeStack.pop();
        if (dataNodeStack.isEmpty()) {
            if (dataNode.hasChildren() || (inclusion == Inclusion.ALWAYS))
                setResult(dataNode);
        } else {
            DataNode parentDataNode = dataNodeStack.peek();

            String fieldName;

            switch (parentDataNode.getNodeType()) {
            case CONTAINER:
            case LIST_ELEMENT:
                DictionaryDataNode dictionaryDataNode =
                        (DictionaryDataNode) parentDataNode;
                fieldName = fieldNameStack.pop();
                if (dataNode.hasChildren() || (inclusion == Inclusion.ALWAYS))
                    dictionaryDataNode.put(fieldName, dataNode);
                break;
            case LEAF_LIST:
                LeafListDataNode leafListDataNode = (LeafListDataNode)
                        parentDataNode;
                leafListDataNode.add(dataNode);
                break;
            case LIST:
                ListDataNode listDataNode = (ListDataNode) parentDataNode;
                listDataNode.add(dataNode);
                break;
            default:
                assert false;
            }
        }
    }

    public void writeFieldStart(String fieldName, boolean optional)
            throws BigDBException {
        pendingFieldName = fieldName;
        pendingFieldOptional = optional;
    }

    public void writeMapStart() throws BigDBException {
        pushSchemaNodeStack();
        DataNode mapDataNode;
        SchemaNode currentSchemaNode = schemaNodeStack.peek();
        if (currentSchemaNode != null) {
            switch (currentSchemaNode.getNodeType()) {
            case CONTAINER:
                mapDataNode = dataNodeFactory.createContainerDataNode(true, null);
                break;
            case LIST_ELEMENT:
                mapDataNode = dataNodeFactory.createListElementDataNode(true, null);
                break;
            default:
                throw new DataNodeTypeMismatchException(
                        "Expected container or list element schema node but current schema node "+currentSchemaNode +" is of type "+currentSchemaNode.getNodeType());
            }
            writeDataNode(mapDataNode);
        }
    }

    public void writeMapFieldStart(String fieldName, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeMapStart();
    }

    public void writeMapFieldStart(String fieldName)
            throws BigDBException {
        writeMapFieldStart(fieldName, false);
    }

    public void writeMapEnd() throws BigDBException {
        writeDataNodeEnd();
        popSchemaNodeStack();
    }

    public void writeListStart() throws BigDBException {
        pushSchemaNodeStack();
        DataNode listDataNode;
        SchemaNode currentSchemaNode = schemaNodeStack.peek();
        switch (currentSchemaNode.getNodeType()) {
        case LIST:
            ListSchemaNode listSchemaNode = (ListSchemaNode) currentSchemaNode;
            IndexSpecifier keySpecifier = listSchemaNode.getKeySpecifier();
            listDataNode = dataNodeFactory.createListDataNode(true, keySpecifier, null);
            break;
        case LEAF_LIST:
            listDataNode = dataNodeFactory.createLeafListDataNode(true, null);
            break;
        default:
            throw new DataNodeTypeMismatchException(
                    "Expected list or leaf-list schema node");
        }

        writeDataNode(listDataNode);
    }

    public void writeListFieldStart(String fieldName, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeListStart();
    }

    public void writeListFieldStart(String fieldName) throws BigDBException {
        writeListFieldStart(fieldName, false);
    }

    public void writeListEnd() throws BigDBException {
        writeDataNodeEnd();
        popSchemaNodeStack();
    }

    public void writeNull() throws BigDBException {
        pushSchemaNodeStack();
        DataNode dataNode = new NullDataNode();
        writeDataNode(dataNode);
        popSchemaNodeStack();
    }

    public void writeBoolean(Boolean value) throws BigDBException {
        if (value == null) {
            writeNull();
        } else {
            pushSchemaNodeStack();
            DataNode dataNode = dataNodeFactory.createLeafDataNode(value);
            writeDataNode(dataNode);
            popSchemaNodeStack();
        }
    }

    public void writeNumber(Long value) throws BigDBException {
        if (value == null) {
            writeNull();
        } else {
            pushSchemaNodeStack();
            DataNode dataNode = dataNodeFactory.createLeafDataNode(value);
            writeDataNode(dataNode);
            popSchemaNodeStack();
        }
    }

    public void writeNumber(Integer value) throws BigDBException {
        if (value == null) {
            writeNull();
        } else {
            pushSchemaNodeStack();
            DataNode dataNode = dataNodeFactory.createLeafDataNode(value.longValue());
            writeDataNode(dataNode);
            popSchemaNodeStack();
        }
    }

    public void writeNumber(Short value) throws BigDBException {
        if (value == null) {
            writeNull();
        } else {
            pushSchemaNodeStack();
            DataNode dataNode = dataNodeFactory.createLeafDataNode(value.longValue());
            writeDataNode(dataNode);
            popSchemaNodeStack();
        }
    }

    public void writeNumber(Byte value) throws BigDBException {
        if (value == null) {
            writeNull();
        } else {
            pushSchemaNodeStack();
            DataNode dataNode = dataNodeFactory.createLeafDataNode(value.longValue());
            writeDataNode(dataNode);
            popSchemaNodeStack();
        }
    }

    public void writeNumber(BigInteger value) throws BigDBException {
        if (value == null) {
            writeNull();
        } else {
            pushSchemaNodeStack();
            DataNode dataNode = dataNodeFactory.createLeafDataNode(value);
            writeDataNode(dataNode);
            popSchemaNodeStack();
        }
    }

    public void writeNumber(Double value) throws BigDBException {
        if (value == null) {
            writeNull();
        } else {
            pushSchemaNodeStack();
            DataNode dataNode = dataNodeFactory.createLeafDataNode(value);
            writeDataNode(dataNode);
            popSchemaNodeStack();
        }
    }

    public void writeNumber(BigDecimal value) throws BigDBException {
        if (value == null) {
            writeNull();
        } else {
            pushSchemaNodeStack();
            DataNode dataNode = dataNodeFactory.createLeafDataNode(value);
            writeDataNode(dataNode);
            popSchemaNodeStack();
        }
    }

    public void writeString(String value) throws BigDBException {
        if (value == null) {
            writeNull();
        } else {
            pushSchemaNodeStack();
            SchemaNode schemaNode = schemaNodeStack.peek();
            if (schemaNode != null) {
                if (schemaNode.getNodeType() != SchemaNode.NodeType.LEAF) {
                    throw new DataNodeTypeMismatchException(
                            "Expected leaf schema node");
                }
                LeafDataNode leafDataNode;
                LeafSchemaNode leafSchemaNode = (LeafSchemaNode) schemaNode;
                TypeSchemaNode typeSchemaNode = leafSchemaNode.getTypeSchemaNode();
                SchemaNode.LeafType leafType = typeSchemaNode.getLeafType();
                switch (leafType) {
                case BOOLEAN:
                    Boolean booleanValue = Boolean.valueOf(value);
                    leafDataNode = dataNodeFactory.createLeafDataNode(booleanValue);
                    break;
                case DECIMAL:
                    BigDecimal decimalValue = new BigDecimal(value);
                    leafDataNode = dataNodeFactory.createLeafDataNode(decimalValue);
                    break;
                case STRING:
                case ENUMERATION:
                case UNION:
                case LEAF_REF:
                    // FIXME: Probably need special handling of enumeration
                    // union, leaf-ref types here?
                    leafDataNode = dataNodeFactory.createLeafDataNode(value);
                    break;
                case INTEGER:
                    Long longValue = Long.valueOf(value);
                    leafDataNode = dataNodeFactory.createLeafDataNode(longValue);
                    break;
                case BINARY:
                    throw new DataNodeTypeMismatchException(
                            "Can't convert from string to binary");
                case NEED_RESOLVE:
                    throw new DataNodeTypeMismatchException(
                            "Invalid unresolved schema node type");
                default:
                    throw new DataNodeTypeMismatchException(
                            "Unexpected leaf type: " + leafType);
                }
                writeDataNode(leafDataNode);
            }
            popSchemaNodeStack();
        }
    }

    public void writeBinary(byte[] value) throws BigDBException {
        if (value == null) {
            writeNull();
        } else {
            pushSchemaNodeStack();
            DataNode dataNode = dataNodeFactory.createLeafDataNode(value);
            writeDataNode(dataNode);
            popSchemaNodeStack();
        }
    }

    public void writeIterable(Iterable<?> iterable) throws BigDBException {
        if (iterable == null) {
            writeNull();
        } else {
            pushSchemaNodeStack();
            SchemaNode schemaNode = schemaNodeStack.peek();
            DataNode dataNode;
            switch (schemaNode.getNodeType()) {
            case LIST:
                ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
                if (listSchemaNode.getKeyNodeNames().isEmpty()) {
                    dataNode =
                            IterableUnkeyedListDataNode.from(schemaNode,
                                    iterable);
                } else {
                    dataNode =
                            IterableKeyedListDataNode
                                    .from(schemaNode, iterable);
                }
                break;
            case LEAF_LIST:
                dataNode = IterableLeafListDataNode.from(schemaNode, iterable);
                break;
            default:
                throw new BigDBException(
                        "Iterable results only allowed for list and leaf-list nodes");
            }

            writeDataNode(dataNode);
            writeDataNodeEnd();
            popSchemaNodeStack();
        }
    }

    public void writeBean(Object bean) throws BigDBException {
        if (bean == null) {
            writeNull();
        } else {
            pushSchemaNodeStack();
            SchemaNode schemaNode = schemaNodeStack.peek();
            DataNode dataNode = new BeanDictionaryDataNode(bean, schemaNode, dataNodeFactory);
            writeDataNode(dataNode);
            writeDataNodeEnd();
            popSchemaNodeStack();
        }
    }

    public void writeBooleanField(String fieldName, Boolean value, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeBoolean(value);
    }

    public void writeNumberField(String fieldName, Long value, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeNumber(value);
    }

    public void writeNumberField(String fieldName, Integer value, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeNumber(value);
    }

    public void writeNumberField(String fieldName, Short value, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeNumber(value);
    }

    public void writeNumberField(String fieldName, Byte value, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeNumber(value);
    }

    public void writeNumberField(String fieldName, BigInteger value, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeNumber(value);
    }

    public void writeNumberField(String fieldName, Double value, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeNumber(value);
    }

    public void writeNumberField(String fieldName, BigDecimal value, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeNumber(value);
    }

    public void writeStringField(String fieldName, String value, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeString(value);
    }

    public void writeBinaryField(String fieldName, byte[] value, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeBinary(value);
    }

    public void writeObjectField(String fieldName, Object value, boolean optional)
            throws BigDBException {
        writeFieldStart(fieldName, optional);
        writeBean(value);
    }

    public void writeBooleanField(String fieldName, Boolean value)
            throws BigDBException {
        writeBooleanField(fieldName, value, false);
    }

    public void writeNumberField(String fieldName, Long value)
            throws BigDBException {
        writeNumberField(fieldName, value, false);
    }

    public void writeNumberField(String fieldName, Integer value)
            throws BigDBException {
        writeNumberField(fieldName, value, false);
    }

    public void writeNumberField(String fieldName, Short value)
            throws BigDBException {
        writeNumberField(fieldName, value, false);
    }

    public void writeNumberField(String fieldName, Byte value)
            throws BigDBException {
        writeNumberField(fieldName, value, false);
    }

    public void writeNumberField(String fieldName, BigInteger value)
            throws BigDBException {
        writeNumberField(fieldName, value, false);
    }

    public void writeNumberField(String fieldName, Double value)
            throws BigDBException {
        writeNumberField(fieldName, value, false);
    }

    public void writeNumberField(String fieldName, BigDecimal value)
            throws BigDBException {
        writeNumberField(fieldName, value, false);
    }

    public void writeStringField(String fieldName, String value)
            throws BigDBException {
        writeStringField(fieldName, value, false);
    }

    public void writeBinaryField(String fieldName, byte[] value)
            throws BigDBException {
        writeBinaryField(fieldName, value, false);
    }

    public void writeObjectField(String fieldName, Object value)
            throws BigDBException {
        writeObjectField(fieldName, value, false);
    }
}
