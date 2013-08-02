package net.bigdb.service.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeFactory;
import net.bigdb.data.DataNodeSerializationException;
import net.bigdb.data.DataNodeTypeMismatchException;
import net.bigdb.data.DataSource;
import net.bigdb.data.DataSourceNotFoundException;
import net.bigdb.data.IndexSpecifier;
import net.bigdb.data.LeafDataNode;
import net.bigdb.data.ListDataNode;
import net.bigdb.data.memory.MemoryDataNodeFactory;
import net.bigdb.query.Query;
import net.bigdb.schema.LeafListSchemaNode;
import net.bigdb.schema.LeafSchemaNode;
import net.bigdb.schema.ListElementSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.SchemaNode;
import net.bigdb.schema.TypeSchemaNode;
import net.bigdb.service.BigDBOperation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.io.CountingOutputStream;

public class DataNodeJsonHandler {
    private final static Logger logger = LoggerFactory.getLogger(DataNodeJsonHandler.class);

    private static final String EMBEDDED_OBJECT_TOKEN_ERROR =
            "Unexpected VALUE_EMBEDDED_OBJECT token while parsing JSON input data";
    private static final String NOT_AVAILABLE_TOKEN_ERROR =
            "Unexpected NOT_AVAILABLE token while parsing JSON input data";
    private static final String END_ARRAY_TOKEN_ERROR =
            "Unexpected END_ARRAY token while parsing JSON input data";

    private final Map<String, DataSource> dataSources;
    private static final DataNodeFactory keyDataNodeFactory =
            new MemoryDataNodeFactory();

    protected final static ObjectMapper mapper;

    static {
        JsonFactory jsonFactory = new JsonFactory();
        jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        mapper = new ObjectMapper(jsonFactory);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(Include.NON_EMPTY);
    }

    public DataNodeJsonHandler(Map<String, DataSource> dataSources) {
        this.dataSources = dataSources;
    }

    public DataNodeJsonHandler(DataSource dataSource) {
        this.dataSources = new HashMap<String, DataSource>();
        dataSources.put(dataSource.getName(), dataSource);
    }

    /**
     * This method creates a leaf data node for the specified data source that
     * has the value of the specified JSON token obtained while parsing the JSON
     * input data.
     *
     * @param parser
     * @param token
     * @param leafSchemaNode
     * @param dataSource
     * @return
     * @throws Exception
     */
    private LeafDataNode parseJsonScalar(JsonParser parser, JsonToken token,
            LeafSchemaNode leafSchemaNode) throws Exception {

        // Get the data source for the leaf node.
        // Leaf nodes should have a single data source assignment
        if(leafSchemaNode.getDataSources().size() != 1) {
            throw new IllegalStateException(
                    "parseJsonScalar: "
                            + parser.getCurrentName()
                            + " - leaf schema node should have exactly 1 associated data sources (found "
                            + leafSchemaNode.getDataSources().size() + ")");
        }
        DataNodeFactory factory = null;
        String dataSourceName = leafSchemaNode.getDataSources().iterator().next();
        DataSource dataSource = this.dataSources.get(dataSourceName);
        if (dataSource != null)
            factory = dataSource.getDataNodeFactory();
        if ((factory == null) && leafSchemaNode.isKey())
            factory = keyDataNodeFactory;
        if (factory == null)
            throw new DataSourceNotFoundException(dataSourceName);
        LeafDataNode returnNode = null;
        TypeSchemaNode type = leafSchemaNode.getTypeSchemaNode();
        SchemaNode.LeafType leafType = type.getLeafType();

        switch (token) {
            case VALUE_FALSE:
            case VALUE_TRUE:
                if (leafType != SchemaNode.LeafType.BOOLEAN)
                    throw new DataNodeTypeMismatchException("Expected boolean value");

                returnNode = factory.createLeafDataNode(token == JsonToken.VALUE_TRUE);
                break;
            case VALUE_NUMBER_INT:
                if (leafType != SchemaNode.LeafType.INTEGER)
                    throw new DataNodeTypeMismatchException("Expected integer value");
                try {
                    returnNode = factory.createLeafDataNode(parser.getLongValue());
                } catch (JsonParseException e) {
                    returnNode = factory.createLeafDataNode(parser.getBigIntegerValue());
                }
                break;
            case VALUE_NUMBER_FLOAT:
                if (leafType != SchemaNode.LeafType.DECIMAL)
                    throw new DataNodeTypeMismatchException("Expected decimal/float value");
                returnNode = factory.createLeafDataNode(parser.getDoubleValue());
                break;
            case VALUE_STRING:
                String s = parser.getText();
                switch (leafType) {
                    case BOOLEAN:
                        returnNode = type.parseDataValueString(factory, s);
                        break;
                    case INTEGER:
                        try {
                            returnNode = type.parseDataValueString(factory, s);
                        } catch (NumberFormatException e) {
                            // TODO: add BigIntegerTypeSchemaNode.
                            returnNode = factory.createLeafDataNode(new BigInteger(s));
                        }
                        break;
                    case STRING:
                        returnNode = type.parseDataValueString(factory, s);
                        break;
                    case ENUMERATION:
                        returnNode = type.parseDataValueString(factory, s);
                        break;
                    case UNION:
                        returnNode = type.parseDataValueString(factory, s);
                        break;
                    case BINARY:
                        throw new BigDBException("Binary data not supported yet");
                    default:
                        throw new BigDBException("Unexpected JSON data type");
                }
                break;
            default:
                throw new BigDBException("Unexpected JSON data type");
        }

        if (returnNode != null)
            leafSchemaNode.validate(returnNode);

        return returnNode;
    }

    private Map<String, DataNode> parseJsonArray(JsonParser parser, SchemaNode schemaNode)
            throws Exception {

        SchemaNode.NodeType nodeType = schemaNode.getNodeType();

        // Get the schema node for each element in the array, depending on
        // whether we're handling a list or a leaf-list.
        LeafSchemaNode leafSchemaNode = null;
        ListElementSchemaNode listElementSchemaNode = null;
        IndexSpecifier keySpecifier = null;
        String elementDataSourceName = null;
        DataSource elementDataSource = null;
        List<DataNode> leafListElements = null;
        Map<String, List<DataNode>> dataSourceMap = null;
        switch (nodeType) {
            case LEAF_LIST:
                leafListElements = new ArrayList<DataNode>();
                LeafListSchemaNode leafListSchemaNode = (LeafListSchemaNode) schemaNode;
                leafSchemaNode = leafListSchemaNode.getLeafSchemaNode();
                elementDataSourceName =
                        leafListSchemaNode.getDataSources().iterator().next();
                elementDataSource = dataSources.get(elementDataSourceName);
                if (elementDataSource == null)
                    throw new DataSourceNotFoundException(elementDataSourceName);
                break;
            case LIST:
                dataSourceMap = new HashMap<String, List<DataNode>>();
                ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
                listElementSchemaNode = listSchemaNode.getListElementSchemaNode();
                keySpecifier = listSchemaNode.getKeySpecifier();
                if ((keySpecifier == null)
                        && (listElementSchemaNode.getDataSources().size() > 1)) {
                    throw new BigDBException("All data for an unkeyed list "
                            + "element must come from a single data source");
                }
                break;
            default:
                throw new DataNodeTypeMismatchException("Expected list or leaf-list node");
        }

        Map<String, DataNode> elementDataSourceMap = null;
        LeafDataNode leafDataNode = null;
        boolean addObject = false;
        boolean addScalar = false;

        boolean endOfArray = false;
        while (!endOfArray) {
            // Get the next token from the parser
            JsonToken token = parser.nextToken();
            if (token == null) {
                // FIXME: Should this be an error?
                break;
            }

            switch (token) {
                case START_OBJECT:
                    if (nodeType != SchemaNode.NodeType.LIST)
                        throw new DataNodeTypeMismatchException(
                                "Expected scalar value for a leaf-list node");
                    assert listElementSchemaNode != null;
                    elementDataSourceMap =
                            parseJsonObject(parser, listElementSchemaNode,
                                    (keySpecifier != null));
                    addObject = true;
                    break;
                case END_OBJECT:
                case START_ARRAY:
                case FIELD_NAME:
                case VALUE_NULL:
                    // FIXME: Should include more information about where the
                    // problem is in the data.
                    throw new BigDBException("Invalid input data");
                case END_ARRAY:
                    endOfArray = true;
                    break;
                case VALUE_FALSE:
                case VALUE_TRUE:
                case VALUE_NUMBER_INT:
                case VALUE_NUMBER_FLOAT:
                case VALUE_STRING:
                    if (nodeType != SchemaNode.NodeType.LEAF_LIST)
                        throw new DataNodeTypeMismatchException(
                                "Expected list element for a list node");
                    assert leafSchemaNode != null;
                    leafDataNode = parseJsonScalar(parser, token, leafSchemaNode);
                    addScalar = true;
                    break;
                case VALUE_EMBEDDED_OBJECT:
                    logger.error(EMBEDDED_OBJECT_TOKEN_ERROR);
                    break;
                case NOT_AVAILABLE:
                    logger.error(NOT_AVAILABLE_TOKEN_ERROR);
                    break;
            }

            if (addObject) {
                assert elementDataSourceMap != null;
                for (Map.Entry<String, DataNode> entry : elementDataSourceMap.entrySet()) {
                    String dataSourceName = entry.getKey();
                    List<DataNode> dataSourceData = dataSourceMap.get(dataSourceName);
                    if (dataSourceData == null) {
                        dataSourceData = new ArrayList<DataNode>();
                        dataSourceMap.put(dataSourceName, dataSourceData);
                    }
                    dataSourceData.add(entry.getValue());
                }
                elementDataSourceMap = null;
                addObject = false;
            }

            if (addScalar) {
                leafListElements.add(leafDataNode);
                leafDataNode = null;
                addScalar = false;
            }
        }

        Map<String, DataNode> result = new HashMap<String, DataNode>();

        if (leafListElements != null) {
            DataNodeFactory factory = elementDataSource.getDataNodeFactory();
            DataNode dataNode = factory.createLeafListDataNode(false, leafListElements);
            result.put(elementDataSourceName, dataNode);
        } else {
            assert dataSourceMap != null;
            // We've gathered all of the fields for the object and separated
            // them
            // into different maps for each data source. Now we construct the
            // immutable data node for each data source with the data.
            for (Map.Entry<String, List<DataNode>> entry : dataSourceMap.entrySet()) {
                String dataSourceName = entry.getKey();
                List<DataNode> listElementDataNodes = entry.getValue();
                DataSource dataSource = dataSources.get(dataSourceName);
                assert dataSource != null;
                DataNodeFactory factory = dataSource.getDataNodeFactory();
                DataNode dataNode =
                        factory.createListDataNode(false, keySpecifier,
                                listElementDataNodes.iterator());
                result.put(dataSourceName, dataNode);
            }
        }

        return result;
    }

    private Map<String, DataNode> parseJsonObject(JsonParser parser,
            SchemaNode schemaNode, boolean ensureKeyFields) throws Exception {
        Map<String, Map<String, DataNode>> dataSourceMap =
                new HashMap<String, Map<String, DataNode>>();
        String fieldName = null;
        DataNode fieldValue = null;
        SchemaNode fieldSchemaNode = null;
        String fieldDataSourceName = null;
        Map<String, DataNode> fieldDataSourceMap = null;
        String dataSourceName;
        DataSource dataSource;

        // Get the key node names if this is keyed list element
        List<String> keyNodeNames = Collections.<String>emptyList();
        Map<String, DataNode> keyNodeValues = null;
        if (schemaNode.getNodeType() == SchemaNode.NodeType.LIST_ELEMENT) {
            ListElementSchemaNode listElementSchemaNode =
                    (ListElementSchemaNode) schemaNode;
            keyNodeNames = listElementSchemaNode.getKeyNodeNames();
            keyNodeValues = new HashMap<String, DataNode>();
        }

        boolean addObject = false;
        boolean addScalar = false;

        boolean endOfObject = false;
        while (!endOfObject) {
            // Get the next token from the parser
            JsonToken token = parser.nextToken();
            if (token == null) {
                // FIXME: Should this be an error?
                break;
            }

            // Create a data node from the data source that has the value of
            // the current token.
            switch (token) {
                case START_OBJECT:
                    // FIXME: SchemaNode should have something like isDictionary method
                    if (fieldSchemaNode.getNodeType() != SchemaNode.NodeType.CONTAINER)
                        throw new DataNodeTypeMismatchException("Unexpected start of object");
                    fieldDataSourceMap = parseJsonObject(parser, fieldSchemaNode, false);
                    addObject = true;
                    break;
                case END_OBJECT:
                    endOfObject = true;
                    break;
                case START_ARRAY:
                    // FIXME: SchemaNode should have something like isList method
                    if ((fieldSchemaNode.getNodeType() != SchemaNode.NodeType.LIST)
                            && (fieldSchemaNode.getNodeType() != SchemaNode.NodeType.LEAF_LIST))
                        throw new DataNodeTypeMismatchException("Unexpected start of list");
                    fieldDataSourceMap = parseJsonArray(parser, fieldSchemaNode);
                    addObject = true;
                    break;
                case END_ARRAY:
                    logger.error(END_ARRAY_TOKEN_ERROR);
                    break;
                case FIELD_NAME:
                    fieldName = parser.getCurrentName();
                    fieldSchemaNode = schemaNode.getChildSchemaNode(fieldName);
                    break;
                case VALUE_NULL:
                    fieldDataSourceMap = getDeletedDataNodeResult(fieldSchemaNode);
                    addObject = true;
                    break;
                case VALUE_FALSE:
                case VALUE_TRUE:
                case VALUE_NUMBER_INT:
                case VALUE_NUMBER_FLOAT:
                case VALUE_STRING:
                    assert fieldSchemaNode != null;
                    if (fieldSchemaNode.getNodeType() != SchemaNode.NodeType.LEAF)
                        throw new DataNodeTypeMismatchException();
                    LeafSchemaNode leafSchemaNode = (LeafSchemaNode) fieldSchemaNode;
                    fieldValue = parseJsonScalar(parser, token, leafSchemaNode);
                    fieldDataSourceName =
                            fieldSchemaNode.getDataSources().iterator().next();
                    addScalar = true;
                    break;
                case VALUE_EMBEDDED_OBJECT:
                    logger.error(EMBEDDED_OBJECT_TOKEN_ERROR);
                    break;
                case NOT_AVAILABLE:
                    logger.error(NOT_AVAILABLE_TOKEN_ERROR);
                    break;
            }
            if (addObject || addScalar) {
                assert fieldName != null;
                assert fieldSchemaNode != null;

                if (addObject) {
                    assert fieldDataSourceMap != null;
                    for (Map.Entry<String, DataNode> entry : fieldDataSourceMap.entrySet()) {
                        dataSourceName = entry.getKey();
                        Map<String, DataNode> dataSourceData =
                                dataSourceMap.get(dataSourceName);
                        if (dataSourceData == null) {
                            dataSourceData = new TreeMap<String, DataNode>();
                            dataSourceMap.put(dataSourceName, dataSourceData);
                        }
                        dataSourceData.put(fieldName, entry.getValue());
                    }
                    addObject = false;
                }
                if (addScalar) {
                    if (keyNodeNames.contains(fieldName)) {
                        keyNodeValues.put(fieldName, fieldValue);
                    }
                    if (dataSources.containsKey(fieldDataSourceName)) {
                        Map<String, DataNode> dataSourceData =
                                dataSourceMap.get(fieldDataSourceName);
                        if (dataSourceData == null) {
                            dataSourceData = new TreeMap<String, DataNode>();
                            dataSourceMap.put(fieldDataSourceName, dataSourceData);
                        }
                        dataSourceData.put(fieldName, fieldValue);
                    }
                    addScalar = false;
                }

                fieldName = null;
                fieldSchemaNode = null;
                fieldDataSourceName = null;
                fieldValue = null;
                fieldDataSourceMap = null;
            }
        }

        // If it's a keyed list element check that all of the key fields
        // are specified.
        if (ensureKeyFields && (keyNodeValues != null)) {
            for (String keyName : keyNodeNames) {
                if (!keyNodeValues.containsKey(keyName)) {
                    throw new BigDBException(
                            "Missing key value in keyed list. key name '" + keyName + "' field name: "+ fieldName + ". Location: "+parser.getCurrentLocation());
                }
            }
        }

        // We've gathered all of the fields for the object and separated them
        // into different maps for each data source. Now we construct the
        // immutable data node for each data source with the data.
        Map<String, DataNode> result = new HashMap<String, DataNode>();
        for (Map.Entry<String, Map<String, DataNode>> entry : dataSourceMap.entrySet()) {
            dataSourceName = entry.getKey();
            Map<String, DataNode> values = entry.getValue();
            // Include all of the key values for the data for all data sources
            if (keyNodeValues != null) {
                // FIXME: Really need to convert the data node values here to
                // one that comes from the factory for this data source.
                // Not an issue now, since all of the current data sources
                // use the MemoryDataNodeFactory.
                values.putAll(keyNodeValues);
            }
            dataSource = dataSources.get(dataSourceName);
            assert dataSource != null;
            DataNodeFactory factory = dataSource.getDataNodeFactory();
            DataNode dataNode;
            switch (schemaNode.getNodeType()) {
                case CONTAINER:
                    dataNode = factory.createContainerDataNode(false, values);
                    break;
                case LIST_ELEMENT:
                    dataNode = factory.createListElementDataNode(false, values);
                    break;
                default:
                    throw new DataNodeTypeMismatchException();
            }
            result.put(dataSourceName, dataNode);
        }

        return result;
    }

    private Map<String, DataNode> getDeletedDataNodeResult(SchemaNode schemaNode)
            throws BigDBException {
        Map<String, DataNode> result = new HashMap<String, DataNode>();
        for (String dataSourceName : schemaNode.getDataSources()) {
            // FIXME: This bypasses the data source's factory to create the
            // deleted data node. Is there a problem with requiring that data
            // sources always use the standard NULL and DELETED NullDataNodes?
            result.put(dataSourceName, DataNode.DELETED);
        }
        return result;
    }

    public Map<String, DataNode> parseData(Query query, BigDBOperation operation,
            SchemaNode schemaNode, InputStream data) throws BigDBException {
        Map<String, DataNode> result;
        JsonFactory jsonFactory = new JsonFactory();
        try {
            JsonParser parser = jsonFactory.createParser(data);
            Stack<SchemaNode> schemaNodeStack = new Stack<SchemaNode>();
            schemaNodeStack.add(schemaNode);

            JsonToken token = parser.nextToken();
            if (token == null)
                return null;

            switch (token) {
                case START_OBJECT:
                    boolean convertToList = (schemaNode.getNodeType() == SchemaNode.NodeType.LIST) &&
                            ((operation == BigDBOperation.INSERT) || (operation == BigDBOperation.REPLACE));
                    SchemaNode objectSchemaNode = schemaNode;
                    ListSchemaNode listSchemaNode = null;
                    if (convertToList) {
                        listSchemaNode = (ListSchemaNode) schemaNode;
                        objectSchemaNode = listSchemaNode.getListElementSchemaNode();
                    }
                    result =
                            parseJsonObject(parser, objectSchemaNode,
                                    convertToList);
                    if (convertToList) {
                        // The data source code expects to be passed a list
                        // data node in this case, so to handle the case of
                        // inserting a single element into a list we convert
                        // the single list element into a list with a single
                        // element here.
                        Map<String, DataNode> convertedResult =
                                new HashMap<String, DataNode>();
                        for (Map.Entry<String, DataNode> entry : result.entrySet()) {
                            String dataSourceName = entry.getKey();
                            DataNode dataNode = entry.getValue();
                            assert dataNode.getNodeType() == DataNode.NodeType.LIST_ELEMENT;
                            DataSource dataSource = dataSources.get(dataSourceName);
                            DataNodeFactory factory = dataSource.getDataNodeFactory();
                            ListDataNode listDataNode =
                                    factory.createListDataNode(false,
                                            listSchemaNode.getKeySpecifier(),
                                            Collections.singletonList(dataNode).iterator());
                            convertedResult.put(dataSourceName, listDataNode);
                        }
                        result = convertedResult;
                    }
                    break;
                case START_ARRAY:
                    result = parseJsonArray(parser, schemaNode);
                    break;
                case VALUE_NULL:
                    result = getDeletedDataNodeResult(schemaNode);
                    break;
                case VALUE_FALSE:
                case VALUE_TRUE:
                case VALUE_NUMBER_INT:
                case VALUE_NUMBER_FLOAT:
                case VALUE_STRING:
                    if (schemaNode.getNodeType() != SchemaNode.NodeType.LEAF)
                        throw new DataNodeTypeMismatchException("Unexpected scalar value");
                    LeafSchemaNode leafSchemaNode = (LeafSchemaNode) schemaNode;
                    DataNode dataNode = parseJsonScalar(parser, token, leafSchemaNode);
                    String dataSourceName = schemaNode.getDataSources().iterator().next();
                    result = new HashMap<String, DataNode>();
                    result.put(dataSourceName, dataNode);
                    break;
                default:
                    throw new BigDBException("Unexpected JSON token: " + token.toString());
            }
        } catch (BigDBException exc) {
            throw exc;
        } catch (Exception exc) {
            throw new BigDBException("Error parsing input JSON data: " + exc, exc);
        }

        return result;
    }

    public long writeToOutputStream(OutputStream out, DataNode root)
            throws DataNodeSerializationException, IOException {
        try {
            CountingOutputStream counting = new CountingOutputStream(out);
            mapper.writeValue(counting, root);
            return counting.getCount();
        } catch (JsonGenerationException e) {
            throw new DataNodeSerializationException(e);
        } catch (JsonMappingException e) {
            throw new DataNodeSerializationException(e);
        }
    }

    public long writeToFile(File file, DataNode root)
            throws DataNodeSerializationException, IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            return writeToOutputStream(fileOutputStream, root);
        }
    }

    public String writeAsString(Object resultObject) throws DataNodeSerializationException {
        try {
            return mapper.writeValueAsString(resultObject);
        } catch (JsonGenerationException e) {
            throw new DataNodeSerializationException(e);
        } catch (JsonMappingException e) {
            throw new DataNodeSerializationException(e);
        } catch (IOException e) {
            throw new DataNodeSerializationException(e);
        }
    }

    public byte[] writeAsByteArray(DataNode root) throws DataNodeSerializationException {
        try {
            return mapper.writeValueAsBytes(root);
        } catch (JsonGenerationException e) {
            throw new DataNodeSerializationException(e);
        } catch (JsonMappingException e) {
            throw new DataNodeSerializationException(e);
        } catch (IOException e) {
            throw new DataNodeSerializationException(e);
        }
    }

    public DataNode readDataNode(InputStream inputStream, SchemaNode schemaNode, String name) throws BigDBException {
        Map<String, DataNode> map =
                parseData(Query.ROOT_QUERY, BigDBOperation.QUERY,
                        schemaNode, inputStream);
        assert map.containsKey(name);
        DataNode root = map.get(name);
        return root;
    }

}
