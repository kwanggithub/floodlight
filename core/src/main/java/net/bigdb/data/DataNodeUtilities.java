package net.bigdb.data;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;
import net.bigdb.data.memory.MemoryContainerDataNode;
import net.bigdb.data.memory.MemoryDataSource;
import net.bigdb.data.memory.MemoryLeafDataNode;
import net.bigdb.data.memory.MemoryListElementDataNode;
import net.bigdb.expression.BinaryOperatorExpression;
import net.bigdb.expression.BooleanLiteralExpression;
import net.bigdb.expression.DecimalLiteralExpression;
import net.bigdb.expression.Expression;
import net.bigdb.expression.IntegerLiteralExpression;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.expression.StringLiteralExpression;
import net.bigdb.query.Step;
import net.bigdb.schema.ListElementSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.SchemaNode;
import net.bigdb.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterators;

/**
 * This class contains static utility methods for dealing with generic data
 * nodes that didn't really seem like they belonged in any of the other data
 * node classes.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class DataNodeUtilities {

    protected final static Logger logger =
            LoggerFactory.getLogger(DataNodeUtilities.class);

    protected final static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(Include.NON_EMPTY);
    }

    /**
     * Convert a data node to a string representation.
     * This uses Jackson to serialize the state to JSON. Currently this is
     * intended as a human-readable format for debugging and for comparing
     * actual and expected results in unit tests. It's not intended to be
     * a serialized format that can be read back in to re-create a data node.
     *
     * @param dataNode
     * @return
     */
    public static String dataNodeToString(DataNode dataNode) {
        String text = null;
        try {
            text = mapper.writeValueAsString(dataNode);
        }
        catch (Exception e) {
            logger.warn("Error converting data node to string: " + e);
            text = String.format("(err: %s)", e);
        }
        return text;
    }

    /**
     * Convert a standard Java scalar object to the LeafDataNode equivalent.
     * Currently this only handle a few input types: String, Boolean, Long,
     * Double, BigDecimal. If we need to we can add more numeric types (e.g.
     * Short, Integer), but currently we never need to handle those.
     *
     * @param object
     * @return
     * @throws BigDBException
     */
    public static LeafDataNode objectToLeafDataNode(Object object)
            throws BigDBException {
        LeafDataNode dataNode = null;
        // FIXME: May eventually need to check for more types here, but
        // for now, these are the only ones that we need to handle.
        if (object instanceof String) {
            dataNode = new MemoryLeafDataNode((String)object);
        } else if (object instanceof Boolean) {
            dataNode = new MemoryLeafDataNode((Boolean)object);
        } else if (object instanceof Long) {
            dataNode = new MemoryLeafDataNode((Long)object);
        } else if (object instanceof Double) {
            dataNode = new MemoryLeafDataNode((Double)object);
        } else if (object instanceof BigDecimal) {
            dataNode = new MemoryLeafDataNode((BigDecimal)object);
        } else {
            throw new BigDBException("Can't convert object to data node");
        }
        return dataNode;
    }

    public static boolean containsLeafValue(DataNode leafList, String searchLeafValue) throws BigDBException {

        for(DataNode child: leafList) {
            if(child.getString().equals(searchLeafValue)) {
                return true;
            }
        }
        return false;
    }

    public static IndexValue getKeyValue(IndexSpecifier keySpecifier, Step step)
            throws BigDBException {
        if (keySpecifier == null)
            return null;
        Map<String, DataNode> keyValues = new HashMap<String, DataNode>();
        // Collect all of the exact match key values in keyValues.
        // If any of the key values are missing then return null
        for (IndexSpecifier.Field keyField: keySpecifier.getFields()) {
            String fieldName = keyField.getName();
            Object keyObject = step.getExactMatchPredicateValue(fieldName);
            if (keyObject == null)
                return null;
            DataNode keyDataNode = objectToLeafDataNode(keyObject);
            keyValues.put(fieldName, keyDataNode);
        }
        IndexValue keyValue = IndexValue.fromValues(keySpecifier, keyValues);
        return keyValue;
    }

    public static IndexValue getKeyValue(ListSchemaNode listSchemaNode,
            Step step) throws BigDBException {
        IndexSpecifier keySpecifier = listSchemaNode.getKeySpecifier();
        return getKeyValue(keySpecifier, step);
    }

    public static IndexValue getKeyValue(IndexSpecifier keySpecifier,
            DataNode listElementDataNode) throws BigDBException {
        Map<String, DataNode> keyValues = new HashMap<String, DataNode>();
        // Collect all of the exact match key values in keyValues.
        // If any of the key values are missing then return null
        for (IndexSpecifier.Field keyField: keySpecifier.getFields()) {
            String fieldName = keyField.getName();
            DataNode keyDataNode = listElementDataNode.getChild(fieldName);
            if (keyDataNode == null)
                return null;
            keyValues.put(fieldName, keyDataNode);
        }
        IndexValue keyValue = IndexValue.fromValues(keySpecifier, keyValues);
        return keyValue;
    }

    public static Step getListElementStep(String stepName, IndexValue keyValue)
            throws BigDBException {
        return getListElementStep(stepName, keyValue.getIndexSpecifier(),
                keyValue.getDataNode());
    }

    public static Step getListElementStep(String stepName,
            IndexSpecifier keySpecifier, DataNode keyDataNode)
                    throws BigDBException {
        Step.Builder builder = new Step.Builder();
        builder.setName(stepName);
        for (IndexSpecifier.Field field: keySpecifier.getFields()) {
            String fieldName = field.getName();
            LocationPathExpression pathExpression =
                    LocationPathExpression.ofStep(Step.of(fieldName));
            DataNode dataNode = keyDataNode.getChild(fieldName);
            if (dataNode.getNodeType() != DataNode.NodeType.LEAF) {
                throw new DataNodeTypeMismatchException(String.format(
                        "Step: \"%s\"; Key field \"%s\" must be a leaf data node, but is %s.",
                        stepName, fieldName, dataNode.getNodeType()));
            }
            Object value = dataNode.getObject();
            Expression valueExpression;
            if (value instanceof Boolean) {
                valueExpression = new BooleanLiteralExpression((Boolean)value);
            } else if (value instanceof String) {
                valueExpression = new StringLiteralExpression((String)value);
            } else if (value instanceof Long) {
                valueExpression = new IntegerLiteralExpression((Long) value);
            } else if (value instanceof BigDecimal) {
                valueExpression = new DecimalLiteralExpression((BigDecimal)value);
            } else {
                String valueClassString = (value != null) ?
                        value.getClass().toString() : "<null>";
                throw new BigDBException("Key field " + fieldName +
                        ": Invalid key data node type: " + valueClassString);
            }
            Expression keyExpression = new BinaryOperatorExpression(
                    BinaryOperatorExpression.Operator.EQ,
                    pathExpression, valueExpression);
            builder.addPredicate(keyExpression);
        }
        return builder.getStep();
    }

    public static LocationPathExpression getListElementLocationPath(
            LocationPathExpression listLocationPath, IndexValue keyValue) {
        // Get the list element path
        int listStepIndex = listLocationPath.size() - 1;
        String listName = listLocationPath.getSteps().get(listStepIndex).getName();
        LocationPathExpression.Builder builder =
                new LocationPathExpression.Builder(true,
                        listLocationPath.getSteps().subList(0, listStepIndex));
        try {
            Step listElementStep =
                    DataNodeUtilities.getListElementStep(listName, keyValue);
            builder.addStep(listElementStep);
        } catch (BigDBException e) {
            throw new BigDBInternalError(
                    "Unexpected exception build list element location path");
        }
        return builder.getPath();
    }

    public static LocationPathExpression getListElementLocationPath(
            LocationPathExpression listLocationPath, int index) {
        int listStepIndex = listLocationPath.size() - 1;
        String listName =
                listLocationPath.getSteps().get(listStepIndex).getName();
        LocationPathExpression.Builder builder =
                new LocationPathExpression.Builder(true,
                        listLocationPath.getSteps().subList(0, listStepIndex));
        try {
            Step listElementStep =
                    DataNodeUtilities.getListElementStep(listName, index);
            builder.addStep(listElementStep);
        } catch (BigDBException e) {
            throw new BigDBInternalError(
                    "Unexpected exception build list element location path");
        }
        return builder.getPath();
    }

    public static Step getListElementStep(String stepName, int index) throws BigDBException {
        Expression indexExpression = new IntegerLiteralExpression((long)index);
        return Step.of(stepName, Collections.singletonList(indexExpression));
    }

    /**
     * Check if a step has predicates that specify an exact match on the key(s)
     * for the given list node. It qualifies as an exact match if there are
     * simple exact match predicates for all of the fields that are specified
     * as key fields for the list. If any of the predicates are missing then
     * it's not recognized as a keyed element lookup and the function returns
     * null.
     *
     * @param listDataNode
     * @param step
     * @return
     * @throws BigDBException
     */
    public static DataNode getKeyedListElement(
            ListDataNode listDataNode, Step step) throws BigDBException {
        IndexSpecifier keySpecifier = listDataNode.getKeySpecifier();
        if (keySpecifier == null)
            return null;
        IndexValue keyValue = getKeyValue(keySpecifier, step);
        if (keyValue == null)
            return null;
        DataNode listElementDataNode = listDataNode.getChild(keyValue);
        if(listElementDataNode.isNull()) {
            return null;
        }
        return listElementDataNode;
    }

    /**
     * Determine if the specified path can match multiple data nodes. This
     * is true if any of the steps in the path before the leaf path component
     * are list nodes and don't have a predicate for a specific element in the
     * list. This function is typically used to determine if a query will
     * return a query result that's a list vs. a single data node.
     *
     * @param path
     * @param baseSchemaNode
     * @return
     * @throws BigDBException
     */
    public static boolean pathMatchesMultipleDataNodes(LocationPathExpression path,
            SchemaNode baseSchemaNode) throws BigDBException {
        SchemaNode schemaNode = baseSchemaNode;
        for (int i = 0; i < path.size(); i++) {
            Step step = path.getStep(i);
            schemaNode = schemaNode.getChildSchemaNode(step.getName());
            switch (schemaNode.getNodeType()) {
            case LIST:
                ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
                IndexSpecifier keySpecifier = listSchemaNode.getKeySpecifier();
                // If it's an unkeyed list, then any predicates for the step
                // can't identify a specific element, so it's possible that
                // it will match multiple list elements.
                if (keySpecifier != null) {
                    // It's a keyed list.
                    // See if the predicates match a specific key.
                    // If not, then it may return multiple list elements.
                    IndexValue keyValue = getKeyValue(keySpecifier, step);
                    if (keyValue == null)
                        return true;
                } else {
                    // It's an unkeyed list.
                    // See if there's an index predicate for a specific element
                    // If not, then it may return multiple list elements.
                    int index = step.getIndexPredicate();
                    if (index < 0)
                        return true;
                }
                break;
            case LEAF_LIST:
                // It's an unkeyed list.
                // See if there's an index predicate for a specific element
                // If not, then it may return multiple list elements.
                int index = step.getIndexPredicate();
                if (index < 0)
                    return true;
                break;
            default:
                break;
            }
        }
        return false;
    }

    /**
     * Wrap a data node in layers to root it at the specified path. This
     * adds additional container and list nodes based on the input path
     * parameter. All list nodes must be fully qualified with exact match
     * predicates for all of the key fields. Unkeyed fields are not supported.
     *
     * @param baseSchemaNode the schema node for the root of the path parameter
     * @param baseKeyValue the index value for the root of the path. This only
     *        needs to be specified if the root is a keyed list node. Otherwise
     *        it can be null, i.e. the common case where the root is the
     *        root container of the overall schema.
     * @param path the path to the specified data node
     * @param dataNode the data node to be wrapped in the rooted data node(s)
     * @return
     * @throws BigDBException
     */
    public static DataNode makeRootedDataNode(SchemaNode baseSchemaNode,
            IndexValue baseKeyValue, LocationPathExpression path,
            DataNode dataNode) throws BigDBException {

        // Base case of the recursion. We've reached the end of the path
        // which corresponds to the data node that was passed in, so we just
        // need to return it without wrapping it.
        if (path.size() == 0) {
            // FIXME: This is a hack. Should be a cleaner way.
            if (baseSchemaNode.getNodeType() == SchemaNode.NodeType.LIST) {
                boolean isListElement = dataNode.isNull() ||
                        (dataNode.getNodeType() == DataNode.NodeType.LIST_ELEMENT);
                if (isListElement) {
                    ListSchemaNode listSchemaNode =
                            (ListSchemaNode) baseSchemaNode;
                    IndexSpecifier keySpecifier =
                            listSchemaNode.getKeySpecifier();
                    Iterator<DataNode> iter = dataNode.isNull() ?
                            Iterators.<DataNode> emptyIterator() :
                            Iterators.singletonIterator(dataNode);
                    dataNode = MemoryDataSource.constructListDataNode(false,
                            keySpecifier, iter);
                }
            }
            return dataNode;
        }

        // Get the next schema node
        Step step = path.getSteps().get(0);
        IndexValue keyValue = null;
        String stepName = step.getName();

        SchemaNode childSchemaNode;
        DataNode childDataNode;
        DataNode rootedDataNode = null;

        // Wrap the returned child data node in another data node. The parent
        // schema node should either be a container or a list, so we wrap
        // appropriately based on the schema node type.
        switch (baseSchemaNode.getNodeType()) {
        case CONTAINER:
        case LIST_ELEMENT:
            childSchemaNode = baseSchemaNode.getChildSchemaNode(stepName);
            if (childSchemaNode.getNodeType() == SchemaNode.NodeType.LIST)
                keyValue = getKeyValue((ListSchemaNode)childSchemaNode, step);
            childDataNode = makeRootedDataNode(childSchemaNode, keyValue,
                    path.subpath(1), dataNode);
            Map<String, DataNode> childNodes =
                    Collections.singletonMap(stepName, childDataNode);
            if (baseSchemaNode.getNodeType() == SchemaNode.NodeType.CONTAINER) {
                rootedDataNode = new MemoryContainerDataNode(false, childNodes);
            } else if (baseKeyValue != null) {
                rootedDataNode = new MemoryListElementDataNode(
                        (MemoryListElementDataNode)baseKeyValue.getDataNode(),
                        false, childNodes, null);
            } else {
                rootedDataNode = new MemoryListElementDataNode(false, childNodes);
            }
            break;
        case LIST:
            ListSchemaNode listSchemaNode = (ListSchemaNode) baseSchemaNode;
            ListElementSchemaNode listElementSchemaNode =
                    listSchemaNode.getListElementSchemaNode();
            childDataNode = makeRootedDataNode(listElementSchemaNode,
                    baseKeyValue, path, dataNode);
            IndexSpecifier keySpecifier = listSchemaNode.getKeySpecifier();
            Iterator<DataNode> iter = Iterators.singletonIterator(childDataNode);
            rootedDataNode = MemoryDataSource.constructListDataNode(false, keySpecifier, iter);
            break;
        default:
            throw new BigDBException("Expected only container and list " +
                    "schema nodes when building rooted data node");
        }

        return rootedDataNode;
    }

    /** safely return a string representation of the datanode digest. If the computation of the
     *  digest fails, returns [err].
     *
     * @param root
     * @return
     */
    public static String getDigestValueStringSafe(DataNode root) {
        try {
            return root.getDigestValue().toString();
        } catch (BigDBException e) {
            if(logger.isDebugEnabled())
                logger.debug("Error computing digest value for node: "+e.getMessage(), e);
            return "[err]";
        }
    }

    /** return a human-readable, reasonably short (<50 chars) string describing the datanode. Intended for debugging */
    public static String debugToString(DataNode node) {
        if(node == null)
            return "(null)";
        else
            return StringUtils.truncate(DataNodeUtilities.dataNodeToString(node), 40, "...") +
                    " ["+DataNodeUtilities.getDigestValueStringSafe(node) + "]";
    }

}
