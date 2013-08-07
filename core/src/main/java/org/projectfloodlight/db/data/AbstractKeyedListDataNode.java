package org.projectfloodlight.db.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.data.DataNodeVisitor.Result;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;

import com.google.common.collect.UnmodifiableIterator;

public abstract class AbstractKeyedListDataNode extends AbstractListDataNode {

    protected static final class KeyedListEntryImpl implements KeyedListEntry {

        private final IndexValue keyValue;
        private final DataNode dataNode;

        public KeyedListEntryImpl(IndexValue keyValue, DataNode dataNode) {
            this.keyValue = keyValue;
            this.dataNode = dataNode;
        }

        public IndexValue getKeyValue() {
            return keyValue;
        }
        public DataNode getDataNode() {
            return dataNode;
        }
    }

    protected static class KeyedListIterator extends UnmodifiableIterator<DataNode> {

        private Iterator<KeyedListEntry> entryIterator;

        protected KeyedListIterator(Iterator<KeyedListEntry> entryIterator) {
            this.entryIterator = entryIterator;
        }

        @Override
        public boolean hasNext() {
            return entryIterator.hasNext();
        }

        @Override
        public DataNode next() {
            KeyedListEntry entry = entryIterator.next();
            return entry.getDataNode();
        }
    }

    @Override
    public boolean isKeyedList() {
        return true;
    }

    @Override
    public DigestValue computeDigestValue() throws BigDBException {
        DigestValue.Builder builder = new DigestValue.Builder();
        builder.update(getNodeType().name());
        builder.update("|");
        IndexSpecifier keySpecifier = getKeySpecifier();
        builder.update(keySpecifier.toString());
        builder.update("|");
        for (KeyedListEntry entry: getKeyedListEntries()) {
            builder.update(entry.getKeyValue().toString());
            builder.update("|");
            builder.update(entry.getDataNode().getDigestValue().toString());
            builder.update("|");
        }
        return builder.getDigestValue();
    }

    // Don't throw BigDBException
    public abstract Iterable<KeyedListEntry> getKeyedListEntries()
            throws BigDBException;

    @Override
    public Iterator<DataNode> iterator() {
        try {
            return new KeyedListIterator(getKeyedListEntries().iterator());
        }
        catch (BigDBException e) {
            throw new BigDBInternalError(
                    "Unexpected error getting keyed list entried", e);
        }
    }

    @Override
    public DataNodeVisitor.Result accept(String name, DataNodeVisitor visitor)
            throws BigDBException {

        Result result = visitor.visitEnterList(name, this);
        if (result == Result.TERMINATE)
            return result;

        if (result != Result.SKIP_SUBTREE) {
            for (KeyedListEntry entry: getKeyedListEntries()) {
                IndexValue keyValue = entry.getKeyValue();
                DataNode childNode = entry.getDataNode();
                result = childNode.accept(keyValue, visitor);
                if ((result == Result.TERMINATE) ||
                        (result == Result.SKIP_SIBLINGS))
                    break;
            }
            if (result == Result.TERMINATE)
                return result;
        }

        result = visitor.visitLeaveList(name, this);

        return result;
    }

    /**
     * Execute the query on the list element and add the result to the overall
     * result list.
     *
     * @param listResult
     *            overall results for the list
     * @param listElementSchemaNode
     *            schema node for the list element
     * @param listElementDataNode
     *            list element data node being queried
     * @param remaingQueryPath
     *            relative path from the list element node for the query. This
     *            path doesn't include the step for the list element
     * @param listName
     *            name of the list
     * @param keyValue
     *            key value of the list element being queried
     * @throws BigDBException
     */
    private void addListElementQueryResults(List<DataNodeWithPath> listResult,
            SchemaNode listElementSchemaNode, DataNode listElementDataNode,
            LocationPathExpression remainingQueryPath, String listName,
            IndexValue keyValue, boolean expandTrailingList)
                    throws BigDBException {
        Step listElementStep =
                DataNodeUtilities.getListElementStep(listName, keyValue);
        LocationPathExpression queryPath =
                LocationPathExpression.ofPaths(LocationPathExpression
                        .ofStep(listElementStep), remainingQueryPath);
        Iterable<DataNodeWithPath> listElementResult =
                listElementDataNode.queryWithPath(listElementSchemaNode,
                        queryPath, expandTrailingList);
        for (DataNodeWithPath dataNodeWithPath : listElementResult) {
            listResult.add(dataNodeWithPath);
        }
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

        Step listStep = queryPath.getStep(0);

        LocationPathExpression remainingQueryPath = queryPath.subpath(1);
        ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
        SchemaNode listElementSchemaNode =
                listSchemaNode.getListElementSchemaNode();

        List<DataNodeWithPath> result = new ArrayList<DataNodeWithPath>();

        if (expandTrailingList || (queryPath.size() > 1)) {
            // See if the predicates specify a single list element by the key
            // field(s) or if we need to iterate.
            IndexValue keyValue =
                    DataNodeUtilities.getKeyValue(getKeySpecifier(), listStep);
            if (keyValue != null) {
                // Single element. Look it up and recursively continue performing
                // the query on the list element.
                DataNode listElementDataNode = getChild(keyValue);
                if (!listElementDataNode.isNull()) {
                    addListElementQueryResults(result, listElementSchemaNode,
                            listElementDataNode, remainingQueryPath,
                            listStep.getName(), keyValue, expandTrailingList);
                }
            } else {
                // Multiple elements. Iterate over all of the list entries,
                // filtering against the specified predicates, and for any matching
                // list elements continue the query operation recursively.
                for (KeyedListEntry keyedListEntry : getKeyedListEntries()) {
                    keyValue = keyedListEntry.getKeyValue();
                    Step listElementStep =
                            DataNodeUtilities.getListElementStep(
                                    listStep.getName(), keyValue);
                    DataNode listElementDataNode = keyedListEntry.getDataNode();
                    if (matchesPredicates(listElementSchemaNode,
                            listElementDataNode, listElementStep, listStep)) {
                        addListElementQueryResults(result, listElementSchemaNode,
                                listElementDataNode, remainingQueryPath, listStep
                                        .getName(), keyValue, expandTrailingList);
                    }
                }
            }
        } else {
            // Return the list node itself rather than expanding to the
            // matching list elements.
            DataNodeWithPath dataNodeWithPath = new DataNodeWithPathImpl(
                    LocationPathExpression.ofStep(Step.of(listStep.getName())),
                    this);
            result.add(dataNodeWithPath);
        }

        return result;
    }

    @Override
    public boolean hasChild(IndexValue keyValue) throws BigDBException {
        DataNode childDataNode = getChild(keyValue);
        return !childDataNode.isNull();
    }

    @Override
    public DataNode getChild(IndexValue keyValue) throws BigDBException {
        for (KeyedListEntry entry: getKeyedListEntries()) {
            if (entry.getKeyValue().equals(keyValue))
                return entry.getDataNode();
        }
        return DataNode.NULL;
    }
}
