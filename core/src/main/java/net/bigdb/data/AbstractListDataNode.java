package net.bigdb.data;

import java.util.Iterator;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.query.Step;
import net.bigdb.query.Step.ExactMatchPredicate;
import net.bigdb.query.Step.PrefixMatchPredicate;
import net.bigdb.schema.SchemaNode;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=AbstractDataNode.ArraySerializer.class)
public abstract class AbstractListDataNode extends AbstractDataNode implements
        ListDataNode {

    protected static final String KEYED_ACCESS_ERROR_MESSAGE =
            "Access by key not allowed with unkeyed list";
    protected static final String INDEXED_ACCESS_ERROR_MESSAGE =
            "Access by index not allowed with keyed list";
    protected static final String DICTIONARY_ACCESS_ERROR_MESSAGE =
            "Access by name not allowed with lists";

    @Override
    public NodeType getNodeType() {
        return NodeType.LIST;
    }

    @Override
    public boolean isIterable() {
        return true;
    }

    @Override
    public int childCount() throws BigDBException {
        // FIXME: This is pretty inefficient because we're incurring the
        // overhead of iterating through all of the element data nodes,
        // but it's going to be inefficient anyway, so clients really shouldn't
        // be using this.
        Iterator<DataNode> iter = iterator();
        int count = 0;
        while (iter.hasNext()) {
            iter.next();
            count++;
        }
        return count;
    }

    @Override
    public boolean hasChildren() throws BigDBException {
        return iterator().hasNext();
    }

    @Override
    public void add(DataNode dataNode) throws BigDBException {
        throw new UnsupportedOperationException(
                "Adding child data nodes is not supported");
    }

    @Override
    public void add(int index, DataNode dataNode) throws BigDBException {
        throw new UnsupportedOperationException(INDEXED_ACCESS_ERROR_MESSAGE);
    }

    @Override
    public void add(IndexValue indexValue, DataNode dataNode)
            throws BigDBException {
        throw new UnsupportedOperationException(KEYED_ACCESS_ERROR_MESSAGE);
    }

    @Override
    public void addAll(Iterable<DataNode> dataNodes) throws BigDBException {
        if (dataNodes != null)
            addAll(dataNodes.iterator());
    }

    @Override
    public void addAll(Iterator<DataNode> dataNodes) throws BigDBException {
        if (dataNodes != null) {
            while (dataNodes.hasNext())
                add(dataNodes.next());
        }
    }

    @Override
    public DataNode remove(int index) throws BigDBException {
        throw new UnsupportedOperationException(INDEXED_ACCESS_ERROR_MESSAGE);
    }

    @Override
    public DataNode remove(IndexValue indexValue)
            throws BigDBException {
        throw new UnsupportedOperationException(KEYED_ACCESS_ERROR_MESSAGE);
    }

    @Override
    public DataNode getChild(IndexValue name) throws BigDBException {
        throw new UnsupportedOperationException(KEYED_ACCESS_ERROR_MESSAGE);
    }

    @Override
    public boolean hasChild(String name) throws BigDBException {
        throw new UnsupportedOperationException(DICTIONARY_ACCESS_ERROR_MESSAGE);
    }

    @Override
    public boolean hasChild(IndexValue name) throws BigDBException {
        throw new UnsupportedOperationException(KEYED_ACCESS_ERROR_MESSAGE);
    }

    /**
     * Type of predicate. Eventually this should be replaced by generic
     * predicate/expression evaluation code.
     */
    public enum PredicateType {
        /** Exact/equals match, e.g. [name="foo"] */
        EXACT_MATCH,
        /**
         * Prefix match, e.g. [starts-with(name, "fo")]
         * The type of the target data node of the predicate must be a string
         */
        PREFIX_MATCH
    };

    /**
     * Test a single predicate against the specified list element data node to
     * see if it matches.
     *
     * @param listElementSchemaNode
     *            schema node of the list element being matched
     * @param listElementDataNode
     *            data node instance being matched
     * @param listElementStep
     *            the fully qualified (i.e. all keys specified) step for the
     *            list element being matched
     * @param predicateType
     *            type of the predicate to evaluate
     * @param predicatePath
     *            path (relative to the list element) to match
     * @param predicateValue
     *            the value to match against
     * @return true if the list element data node matches the specified
     *         predicate; else return false
     * @throws BigDBException
     */
    public static boolean matchesPredicate(SchemaNode listElementSchemaNode,
            DataNode listElementDataNode, Step listElementStep,
            PredicateType predicateType, LocationPathExpression predicatePath,
            Object predicateValue) throws BigDBException {

        // The query path must include the step for the list element, so we
        // construct that path by prepending the list element step to the
        // path specified in the predicate
        LocationPathExpression queryPath =
                LocationPathExpression.ofPaths(LocationPathExpression
                        .ofStep(listElementStep), predicatePath);

        // Perform the query to look up the value of the data node specified
        // in the predicatePath argument
        Iterable<DataNodeWithPath> result =
                listElementDataNode.queryWithPath(listElementSchemaNode,
                        queryPath, true);

        SchemaNode leafSchemaNode =
                listElementSchemaNode.getDescendantSchemaNode(predicatePath
                        .getSimplePath());
        boolean caseSensitive = leafSchemaNode.getBooleanAttributeValue(
                SchemaNode.CASE_SENSITIVE_ATTRIBUTE_NAME, true);

        // Resolve the predicate path to a data node.
        for (DataNodeWithPath dataNodeWithPath: result) {
            DataNode dataNode = dataNodeWithPath.getDataNode();

            // Convert the predicate and data node values to strings
            String predicateValueString = predicateValue.toString();
            Object dataNodeValue = dataNode.getObject();
            // FIXME: Can the object from a leaf data node ever be null?
            // i.e. do we need to check for null here?
            String dataNodeValueString = (dataNodeValue != null) ? dataNodeValue.toString() : "";

            // Adjust the string values if the node is case-insensitive
            if (!caseSensitive) {
                predicateValueString = predicateValueString.toLowerCase();
                dataNodeValueString = dataNodeValueString.toLowerCase();
            }

            // Evaluate the predicate. Only exact match and prefix match are
            // supported for now.
            // FIXME: Eventually need more general predicate/expression
            // evaluation logic.
            switch (predicateType) {
            case EXACT_MATCH:
                if (predicateValueString.equals(dataNodeValueString))
                    return true;
                break;
            case PREFIX_MATCH:
                if (dataNodeValueString.startsWith(predicateValueString))
                    return true;
                break;
            default:
                throw new UnsupportedOperationException("Unsupported predicate");
            }
        }
        return false;
    }

    /**
     * Match all of the predicates specified in the queryStep argument. The
     * list element matches only if all of the predicates match.
     *
     * @param listElementSchemaNode
     *            schema node of the list element being matched
     * @param listElementDataNode
     *            data node instance being matched
     * @param listElementStep
     *            the fully qualified (i.e. all keys specified) step for the
     *            list element being matched
     * @param queryStep
     *            the query step to be evaluated
     * @return true if the list element data node matches the specified
     *         predicate; else return false
     * @throws BigDBException
     */
    public static boolean matchesPredicates(SchemaNode listElementSchemaNode,
            DataNode listElementDataNode, Step listElementStep, Step queryStep)
                    throws BigDBException {

        assert listElementSchemaNode != null;
        assert listElementDataNode != null;
        assert listElementStep != null;

        // Check if there are no predicates to evaluate
        int predicateCount = queryStep.getPredicates().size();
        if (predicateCount == 0)
            return true;

        // Get the lists of exact match and prefix match predicates
        List<ExactMatchPredicate> exactMatchPredicates =
                queryStep.getExactMatchPredicates();
        int exactMatchPredicateCount =
                (exactMatchPredicates != null) ? exactMatchPredicates.size()
                        : 0;
        List<PrefixMatchPredicate> prefixMatchPredicates =
                queryStep.getPrefixMatchPredicates();
        int prefixMatchPredicateCount =
                (prefixMatchPredicates != null) ? prefixMatchPredicates.size()
                        : 0;

        // Make sure there aren't any other (unsupported) predicates
        // specified in the step
        if (predicateCount > exactMatchPredicateCount +
                prefixMatchPredicateCount)
            throw new BigDBException("Unsupported predicate");

        // Evaluate the exact match predicates
        if (exactMatchPredicates != null) {
            for (ExactMatchPredicate exactMatchPredicate : exactMatchPredicates) {
                LocationPathExpression path = exactMatchPredicate.getPath();
                Object value = exactMatchPredicate.getValue();
                if (!matchesPredicate(listElementSchemaNode,
                        listElementDataNode, listElementStep,
                        PredicateType.EXACT_MATCH, path, value)) {
                    return false;
                }
            }
        }

        // Evaluate the prefix match predicates
        if (prefixMatchPredicates != null) {
            for (PrefixMatchPredicate prefixMatchPredicate : prefixMatchPredicates) {
                String pathString = prefixMatchPredicate.getName();
                String prefix = prefixMatchPredicate.getPrefix();
                LocationPathExpression path =
                        LocationPathExpression.parse(pathString);
                if (!matchesPredicate(listElementSchemaNode,
                        listElementDataNode, listElementStep,
                        PredicateType.PREFIX_MATCH, path, prefix)) {
                    return false;
                }
            }
        }

        return true;
    }
}
