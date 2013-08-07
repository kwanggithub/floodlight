package org.projectfloodlight.db.data;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.AggregateSchemaNode;
import org.projectfloodlight.db.schema.InvalidSchemaTypeException;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;

import com.google.common.collect.UnmodifiableIterator;

/**
 * This class is a data node that represents the differences between two
 * underlying dictionary data nodes. The differences are computed on demand
 * as child nodes are requested or as client code iterates over the child
 * nodes in the dictionary data node. The diff data node contains child data
 * nodes for any child data node differences between the old and new data nodes.
 * Child nodes that are deleted in the new data node are represented as child
 * nodes whose value is a NullDataNode. To differentiate between a NullDataNode
 * result from getChild that means the child doesn't exist and that the child
 * node has been deleted, a special DataNode.DELETED value is returned from
 * getChild for child nodes that have been deleted. Client code can do an
 * object comparison with DataNode.DELETED to differentiate. Alternatively,
 * clients can use hasChild to differentiate or use getDictionaryEntries to
 * iterate over all of the child nodes that exist in the diff data node.
 *
 * The implementation relies on the getDigestValue method in the data nodes
 * to determine if 2 nodes are the same, so you can only diff data nodes that
 * have valid digest values. In particular this means that the data nodes must
 * be immutable. Also, currently logical data nodes don't support digest values
 * so you can't diff them. The workaround for that is that instead of creating
 * a diff data node between two logical trees you would instead create a logical
 * data node whose contributions are the per-data-source diff data nodes.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class DiffDictionaryDataNode extends AbstractDictionaryDataNode {

    /**
     * Implementation class of DictionaryEntry iterator interface. It
     * iterates over all of the possible child names (as obtained from
     * getAllChildNames) returning entries for each one that is different
     * between the old and new data nodes. Most of the work is actually done
     * in the getChild method, which does all of the checks and digest value
     * comparisons to determine if the old and new nodes are different.
     */
    private class DiffEntryIterator extends UnmodifiableIterator<DictionaryEntry> {

        private Iterator<String> childNameIterator;
        private DictionaryEntry pendingEntry = null;

        /** Advance the pending entry to the next available difference */
        private void updatePendingEntry() {
            while ((pendingEntry == null) && childNameIterator.hasNext()) {
                String childName = childNameIterator.next();
                try {
                    DataNode childDataNode = getChild(childName);
                    if (!childDataNode.isNull() || childDataNode == DELETED)
                        pendingEntry = new DictionaryEntryImpl(childName, childDataNode);
                } catch (BigDBException e) {
                    // This error should never happen, since we're calling
                    // getChild with the values return from getChildNames,
                    // so we should never get a SchemaNodeNotFoundException.
                    throw new BigDBInternalError(String.format(
                            "Unexpected error getting child \"%s\" for schema node \"%s\".",
                            childName, schemaNode.toString()), e);
                }
            }
        }

        DiffEntryIterator() {
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
            pendingEntry = null;
            updatePendingEntry();
            return returnEntry;
        }
    }

    private class DiffEntryIterable implements Iterable<DictionaryEntry> {
        @Override
        public Iterator<DictionaryEntry> iterator() {
            return new DiffEntryIterator();
        }
    }

    /** The schema node corresponding to this diff data node. This should
     * be either a ContainerSchemaNode or ListElementSchemaNode.
     */
    private AggregateSchemaNode schemaNode;

    /** The old data node from which to compute the differences */
    private DataNode oldDataNode;

    /** The new data node from which to compute the differences */
    private DataNode newDataNode;

    protected DiffDictionaryDataNode(SchemaNode schemaNode,
            DataNode oldDataNode, DataNode newDataNode) throws BigDBException {

        super();

        assert schemaNode != null;
        assert oldDataNode != null;
        assert newDataNode != null;

        this.schemaNode = (AggregateSchemaNode) schemaNode;
        this.oldDataNode = oldDataNode;
        this.newDataNode = newDataNode;

        freeze();
    }

    @Override
    public DigestValue computeDigestValue() {
        // Digest values are disabled for diff data nodes, because to compute
        // the digest value we'd need to iterate over the entire contents of
        // the node which goes against the on-demand approach to generating
        // the differences. Note, though, that the target old and new data nodes
        // that the diff data nodes operates on MUST support getDigestValue.
        return null;
    }

    @Override
    public Iterable<DictionaryEntry> getDictionaryEntries() {
        return new DiffEntryIterable();
    }

    @Override
    public boolean hasChild(String name) throws BigDBException {
        DataNode childNode = getChild(name);
        return !childNode.isNull() || childNode == DELETED;
    }

    @Override
    public DataNode getChild(Step step) throws BigDBException {

        String name = step.getName();

        // Get the schema node for the requested child. If the name argument
        // doesn't map to a child that's defined in the schema, then this will
        // throw a SchemaNodeNotFoundException.
        SchemaNode childSchemaNode = schemaNode.getChildSchemaNode(name);

        DataNode oldChildDataNode = oldDataNode.getChild(step);
        DataNode newChildDataNode = newDataNode.getChild(step);

        if (oldChildDataNode.isNull()) {
            // Just return newChildDataNode. If newChildDataNode is also NULL,
            // then returning it is correct, because NULL means no difference.
            // If newChildDataNode is not NULL, then that means that it's a new
            // node, and returning it is again the correct thing to do.
            return newChildDataNode;
        }

        if (newChildDataNode.isNull()) {
            // If the type of the NULL child data node is anything other than
            // LIST, then we just return that the node has been deleted. If
            // it's a LIST, then we want to continue on to create a
            // DiffKeyedListDataNode, so that the diffs include the exact list
            // elements that were deleted. If we don't do that then the code
            // that depends on the diff (e.g. the validation hooks) don't get
            // the specific information about which list elements were deleted,
            // which in many cases is useful information for them.
            if (childSchemaNode.getNodeType() != SchemaNode.NodeType.LIST)
                return DELETED;
        } else {
            // The child data node exists in both the new and the old tree.
            // Check the digest values to see if the nodes are different.
            // If they're the same, then just return DataNode.NULL to indicate
            // no differences. Otherwise, continue on to the code that returns
            // the appropriate data node depending on the node type.
            DigestValue oldDigestValue = oldChildDataNode.getDigestValue();
            assert oldDigestValue != null;
            DigestValue newDigestValue = newChildDataNode.getDigestValue();
            assert newDigestValue != null;
            if (oldDigestValue.equals(newDigestValue))
                return DataNode.NULL;
        }

        // FIXME: This is sort of kludgy to switch on the node type.
        // There's probably a better way to do this.
        switch (childSchemaNode.getNodeType()) {
        case CONTAINER:
            return new DiffContainerDataNode(childSchemaNode,
                    oldChildDataNode, newChildDataNode);
        case LIST:
            ListSchemaNode listSchemaNode = (ListSchemaNode) childSchemaNode;
            if (listSchemaNode.getKeySpecifier() == null)
                throw new BigDBException("Unkeyed list nodes cannot be diffed");
            return new DiffKeyedListDataNode(childSchemaNode,
                    oldChildDataNode, newChildDataNode);
        case LEAF:
        case LEAF_LIST:
            return newChildDataNode;
        default:
            throw new InvalidSchemaTypeException();
        }
    }

    @Override
    public Set<String> getAllChildNames() {
        return Collections.unmodifiableSet(schemaNode.getChildNodes().keySet());
    }

}
