package org.projectfloodlight.db.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.data.DataNodeVisitor.Result;
import org.projectfloodlight.db.data.memory.MemoryContainerDataNode;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.SchemaNode;
import org.projectfloodlight.db.util.Path;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;

@JsonSerialize(using=AbstractDataNode.DictionarySerializer.class)
public abstract class AbstractDictionaryDataNode extends AbstractDataNode {

    protected static final class DictionaryEntryImpl implements DictionaryEntry {

        private final String name;
        private final DataNode dataNode;

        public DictionaryEntryImpl(String name, DataNode dataNode) {
            this.name = name;
            this.dataNode = dataNode;
        }

        public String getName() {
            return name;
        }

        public DataNode getDataNode() {
            return dataNode;
        }
    }

    /**
     * Iterator over the entries for the dictionary data node.
     * The iteration is driven by an iterator over all of the possible child
     * nodes names as returned by getAllChildNames. For each name getChild is
     * called to see if that child is included in the logical data node (or if
     * a default value is defined in the schema).
     */
    protected final class EntryIteratorImpl extends UnmodifiableIterator<DictionaryEntry> {

        /** Iterator over all of the child names */
        private final Iterator<String> childNameIterator;

        /** The next entry to return from next().
         * When we're at the end of the iterator this value is null.
         */
        private DictionaryEntry pendingEntry;

        /** Advance the pending entry the next available entry */
        private void updatePendingEntry() {
            pendingEntry = null;
            String childName = null;
            try {
                while (childNameIterator.hasNext()) {
                    childName = childNameIterator.next();
                    DataNode dataNode = getChild(childName);
                    if (!dataNode.isNull() || (dataNode == DataNode.DELETED)) {
                        pendingEntry = new DictionaryEntryImpl(childName, dataNode);
                        break;
                    }
                }
            }
            catch (BigDBException e) {
                throw new BigDBInternalError(String.format(
                        "Unexpected exception getting child name \"%s\" of dictionary node",
                        childName), e);
            }
        }

        EntryIteratorImpl() {
            childNameIterator = getAllChildNames().iterator();
            updatePendingEntry();
        }

        @Override
        public boolean hasNext() {
            return pendingEntry != null;
        }

        @Override
        public DictionaryEntry next() {
            if (pendingEntry == null)
                throw new NoSuchElementException();

            DictionaryEntry returnEntry = pendingEntry;
            updatePendingEntry();
            return returnEntry;
        }
    }

    protected class EntryIterableImpl implements Iterable<DictionaryEntry> {
        @Override
        public Iterator<DictionaryEntry> iterator() {
            return new EntryIteratorImpl();
        }
    }

    protected static class DictionaryIterator extends UnmodifiableIterator<DataNode> {

        private Iterator<DictionaryEntry> entryIterator;

        protected DictionaryIterator(Iterator<DictionaryEntry> entryIterator) {
            this.entryIterator = entryIterator;
        }

        @Override
        public boolean hasNext() {
            return entryIterator.hasNext();
        }

        @Override
        public DataNode next() {
            DictionaryEntry entry = entryIterator.next();
            return entry.getDataNode();
        }
    }

    @Override
    public boolean isIterable() {
        return true;
    }

    @Override
    public boolean isDictionary() {
        return true;
    }

    @Override
    public Iterator<DataNode> iterator() {
        return new DictionaryIterator(getDictionaryEntries().iterator());
    }

    @Override
    public Iterable<DictionaryEntry> getDictionaryEntries() {
        return new EntryIterableImpl();
    }

    @Override
    public boolean hasChild(String name) throws BigDBException {
        DataNode childNode = getChild(name);
        return !childNode.isNull();
    }

    @Override
    public int childCount() throws BigDBException {
        // Use the iterator (and indirectly getChild()) to determine how many
        // children are present across all of the contributions.
        // This is pretty inefficient
        Iterator<DictionaryEntry> iter = getDictionaryEntries().iterator();
        int count = 0;
        while (iter.hasNext()) {
            iter.next();
            count++;
        }
        return count;
    }

    @Override
    public boolean hasChildren() throws BigDBException {
        Iterator<DictionaryEntry> iter = getDictionaryEntries().iterator();
        return iter.hasNext();
    }

    @Override
    public DigestValue computeDigestValue() throws BigDBException {
        DigestValue.Builder builder = new DigestValue.Builder();
        builder.update(getNodeType().name() + "|");
        for (DataNode.DictionaryEntry entry: getDictionaryEntries()) {
            builder.update(String.format("%s|%s:|", entry.getName(),
                    entry.getDataNode().getDigestValue().toString()));
        }
        return builder.getDigestValue();
    }

    private Result acceptChildren(DataNodeVisitor visitor) throws BigDBException {
        Result result = Result.CONTINUE;
        for (DictionaryEntry entry: getDictionaryEntries()) {
            String childName = entry.getName();
            DataNode childNode = entry.getDataNode();
            result = childNode.accept(childName, visitor);
            if ((result == Result.TERMINATE) ||
                    (result == Result.SKIP_SIBLINGS)) {
                break;
            }
        }
        return result;
    }

    @Override
    public Result accept(String name, DataNodeVisitor visitor)
            throws BigDBException {

        Result result = visitor.visitEnterContainer(name, (ContainerDataNode) this);
        if (result == Result.TERMINATE)
            return result;

        if (result != Result.SKIP_SUBTREE) {
            result = acceptChildren(visitor);
            if (result == Result.TERMINATE)
                return result;
        }

        result = visitor.visitLeaveContainer(name, (ContainerDataNode) this);

        return result;
    }

    @Override
    public Result accept(IndexValue keyValue, DataNodeVisitor visitor)
            throws BigDBException {

        Result result = visitor.visitEnterListElement(keyValue, (ListElementDataNode) this);
        if (result == Result.TERMINATE)
            return result;

        if (result != Result.SKIP_SUBTREE) {
            result = acceptChildren(visitor);
            if (result == Result.TERMINATE)
                return result;
        }

        result = visitor.visitLeaveListElement(keyValue, (ListElementDataNode) this);

        return result;
    }

    protected abstract Set<String> getAllChildNames();

    /**
     * @returns the set of names of the child data nodes
     */
    @Override
    public Set<String> getChildNames() {
        Set<String> childNames = new TreeSet<String>();
        for (DictionaryEntry entry: getDictionaryEntries()) {
            childNames.add(entry.getName());
        }
        return childNames;
    }

    @Override
    protected Iterable<DataNodeWithPath> queryWithPath(SchemaNode schemaNode,
            LocationPathExpression queryPath, boolean expandTrailingList,
            boolean includeEmptyContainers) throws BigDBException {

        LocationPathExpression basePath;
        int childStartIndex;
        if (queryPath.isAbsolute()) {
            basePath = LocationPathExpression.ROOT_PATH;
            childStartIndex = 0;
        } else {
            if (queryPath.size() == 0)
                throw new BigDBException("Query path argument cannot be empty");
            basePath = queryPath.subpath(0, 1);
            childStartIndex = 1;
        }

        // If we're at the end of the query path then just return the current node
        if (childStartIndex == queryPath.size()) {
            DataNodeWithPath dataNodeWithPath = new DataNodeWithPathImpl(queryPath, this);
            return Collections.<DataNodeWithPath>singletonList(dataNodeWithPath);
        }

        // Advance to the child node specified in the query path
        Step childStep = queryPath.getStep(childStartIndex);
        String childName = childStep.getName();
        DataNode childNode = getChild(childStep);
        // For now we have the requirement that implementations of the DataNode
        // interface must derive from the AbstractDataNode class. This is only
        // so that we can access the protected overload of queryWithPath that
        // lets us specify whether or not to include empty containers.
        // Eventually we could clean this up a bit by having a DataNodeImpl
        // interface that adds overload of queryWithPath. The whole point is
        // that we don't want that internal method to be in the public API as
        // defined by the DataNode interface.
        if (!(childNode instanceof AbstractDataNode))
            throw new BigDBInternalError(
                    "Data node implementations must subclass AbstractDataNode");
        AbstractDataNode childNodeImpl = (AbstractDataNode) childNode;
        SchemaNode childSchemaNode = schemaNode.getChildSchemaNode(childName);

        // Query the child with the remaining path
        LocationPathExpression remainingQueryPath =
                LocationPathExpression.of(false, queryPath.getSteps().subList(
                        childStartIndex, queryPath.size()));
        Iterable<DataNodeWithPath> childResult = childNodeImpl.queryWithPath(
                childSchemaNode, remainingQueryPath, expandTrailingList, false);

        // Adjust the paths in the results returned from the child
        List<DataNodeWithPath> result = new ArrayList<DataNodeWithPath>();
        for (DataNodeWithPath childDataNodeWithPath : childResult) {
            LocationPathExpression parentPath =
                    LocationPathExpression.ofPaths(basePath,
                            childDataNodeWithPath.getPath());
            DataNodeWithPath dataNodeWithPath =
                    new DataNodeWithPathImpl(parentPath, childDataNodeWithPath
                            .getDataNode());
            result.add(dataNodeWithPath);
        }

        if (includeEmptyContainers && !result.iterator().hasNext()) {
            Path path = queryPath.getSimplePath();
            // If we're at the root container node and we're resolving an
            // absolute path then we want to convert that absolute path into
            // the relative path with the same components. In all other cases
            // the first component in the path corresponds to the current
            // dictionary data node, so we need to strip off the leading
            // component to get the path for the descendant nodes.
            path = (path.getType() == Path.Type.ABSOLUTE)
                    ? new Path(Path.Type.RELATIVE, path.getComponents())
                    : path.getSubPath(1);
            if (schemaNode.isNestedContainerPath(path)) {
                DataNodeWithPath dataNodeWithPath =
                        new DataNodeWithPathImpl(queryPath,
                                new MemoryContainerDataNode(false, null));
                result = ImmutableList.of(dataNodeWithPath);
            }
        }

        return result;
    }
}
