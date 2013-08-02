package net.bigdb.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;
import net.bigdb.schema.ListElementSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.SchemaNode;

import com.google.common.collect.AbstractIterator;

/**
 * Implementation of a keyed list data node that merges the contributions from
 * one or more underlying list data nodes.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class CompoundKeyedListDataNode extends AbstractKeyedListDataNode {

    protected final static Logger logger = LoggerFactory
            .getLogger(CompoundKeyedListDataNode.class);

    public static class Builder {

        private SchemaNode schemaNode;
        private Set<DataNode> dataNodes = new HashSet<DataNode>();

        public Builder(SchemaNode schemaNode) {
            this.schemaNode = schemaNode;
        }

        public Builder addDataNode(DataNode dataNode) {
            dataNodes.add(dataNode);
            return this;
        }

        public CompoundKeyedListDataNode build() {
            return CompoundKeyedListDataNode.from(schemaNode, dataNodes);
        }
    }

    /**
     * Implementation of the entry iterator that keeps track of the state of the
     * underlying iterators over the contributions from each of the data sources.
     */
    private class CompoundKeyedListEntryIterator extends AbstractIterator<KeyedListEntry> {

        /**
         * This class keeps track of the iterator state for a single data
         * node contribution.
         */
        private class ContributionInfo {

            /**
             * The iterator over the entries in the list data node for this
             * contribution.
             */
            Iterator<KeyedListEntry> entryIterator;

            /**
             * The next entry to return for this contribution.
             * When we've consumed all of the entries for this contribution
             * then this is set to null.
             */
            KeyedListEntry pendingEntry;

            ContributionInfo(Iterator<KeyedListEntry> entryIterator) {
                this.entryIterator = entryIterator;
                updatePendingEntry();
            }

            /** Advance the pending list element and associated key value */
            void updatePendingEntry() {
                IndexValue previousKeyValue =
                        (pendingEntry != null) ? pendingEntry.getKeyValue()
                                : null;
                pendingEntry =
                        entryIterator.hasNext() ? entryIterator.next() : null;
                if ((pendingEntry != null) &&
                        (previousKeyValue != null) &&
                        (pendingEntry.getKeyValue().compareTo(previousKeyValue) <= 0)) {
                    throw new KeyedListIteratorNotSortedException(
                            previousKeyValue, pendingEntry.getKeyValue());
                }
            }
        }

        /** The contributions from the different underling data nodes */
        private List<ContributionInfo> iteratorContributions;

        CompoundKeyedListEntryIterator() {
            iteratorContributions = new ArrayList<ContributionInfo>();
            try {
                for (DataNode dataNode : dataNodes) {
                    Iterator<KeyedListEntry> entryIterator =
                            dataNode.getKeyedListEntries().iterator();
                    ContributionInfo iteratorContribution =
                            new ContributionInfo(entryIterator);
                    iteratorContributions.add(iteratorContribution);
                }
            } catch (BigDBException e) {
                throw new BigDBInternalError(
                        "Error building logical keyed list entry iterator", e);
            }
        }

        @Override
        protected KeyedListEntry computeNext() {
            // First find the smallest key value across all of the
            // contributing iterators
            IndexValue smallestKeyValue = null;
            for (ContributionInfo contribution : iteratorContributions) {
                if (contribution.pendingEntry != null) {
                    IndexValue pendingKeyValue =
                            contribution.pendingEntry.getKeyValue();
                    if ((smallestKeyValue == null) ||
                            (pendingKeyValue.compareTo(smallestKeyValue) < 0)) {
                        smallestKeyValue = pendingKeyValue;
                    }
                }
            }

            // Check if there are more elements
            if (smallestKeyValue == null)
                return endOfData();

            // Create a logical data node for the list element that contains
            // the contributions from all of the data sources for the key
            // value we found above.
            ListElementSchemaNode listElementSchemaNode =
                    schemaNode.getListElementSchemaNode();
            Set<DataNode> listElementContributions = new HashSet<DataNode>();
            for (ContributionInfo contribution : iteratorContributions) {
                KeyedListEntry entry = contribution.pendingEntry;
                if ((entry != null) &&
                        smallestKeyValue.equals(entry.getKeyValue())) {
                    listElementContributions.add(entry.getDataNode());
                    contribution.updatePendingEntry();
                }
            }
            assert listElementContributions.size() > 0;
            DataNode listElementDataNode =
                    (listElementContributions.size() == 1)
                            ? listElementContributions.iterator().next()
                            : CompoundDictionaryDataNode.from(
                                    listElementSchemaNode,
                                    listElementContributions);
            return new KeyedListEntryImpl(smallestKeyValue, listElementDataNode);
        }
    }

    private class CompoundKeyedListEntryIterable implements Iterable<KeyedListEntry> {
        @Override
        public Iterator<KeyedListEntry> iterator() {
            return new CompoundKeyedListEntryIterator();
        }
    }

    /** Schema node corresponding to the dictionary data node */
    private final ListSchemaNode schemaNode;

    /** The underlying list data nodes that contribute to the compound data node */
    private final Set<DataNode> dataNodes;

    private CompoundKeyedListDataNode(SchemaNode schemaNode,
            Set<DataNode> dataNodes) {

        super();

        assert schemaNode != null;
        assert schemaNode instanceof ListSchemaNode;
        assert dataNodes != null;

        this.schemaNode = (ListSchemaNode) schemaNode;
        this.dataNodes = dataNodes;
    }

    /**
     * Factory method to create a new compound keyed list data node.
     *
     * @param schemaNode
     *            the schema node corresponding to the data node
     * @param dataNodes
     *            the data node contributions to the compound data node. All of
     *            the contributing data nodes should also be keyed list data
     *            nodes, i.e. isKeyedList() should be true. And they all should
     *            have the same key specifier. The new keyed list data node
     *            assumes ownership of the dataNodes set. The caller should not
     *            make any modifications to the set after calling this factory
     *            method.
     * @return the newly constructed compound keyed list data node
     */
    public static CompoundKeyedListDataNode from(SchemaNode schemaNode,
            Set<DataNode> dataNodes) {
        return new CompoundKeyedListDataNode(schemaNode, dataNodes);
    }

    @Override
    public DigestValue computeDigestValue() {
        // Computing the digest values for logical data nodes would be
        // expensive so for now we just don't support it.
        // We'll see if it's needed.
        return null;
    }

    @Override
    public Iterable<KeyedListEntry> getKeyedListEntries() {
        return new CompoundKeyedListEntryIterable();
    }

    @Override
    public IndexSpecifier getKeySpecifier() {
        return schemaNode.getKeySpecifier();
    }

    @Override
    public boolean hasChild(IndexValue keyValue) throws BigDBException {
        // See if any of the contributing data nodes contain a child with the
        // specified key value.
        for (DataNode dataNode: dataNodes) {
            if (dataNode.hasChild(keyValue))
                return true;
        }
        return false;
    }

    @Override
    public DataNode getChild(IndexValue keyValue) throws BigDBException {
        // Get the set of list elements that contain the specified key value
        Set<DataNode> childDataNodes = new HashSet<DataNode>();
        for (DataNode dataNode: dataNodes) {
            DataNode childDataNode = dataNode.getChild(keyValue);
            if (!childDataNode.isNull()) {
                childDataNodes.add(childDataNode);
            }
        }

        switch (childDataNodes.size()) {
        case 0:
            // None of the contributing contained a list element with the
            // specified key value, so return NULL.
            return DataNode.NULL;
        case 1:
            // Only one of the contributions contained the list element so we
            // can just return the child node from that contribution directly.
            return childDataNodes.iterator().next();
        default:
            // Multiple contributions. Create a compound dictionary data node
            // to merge the contributions.
            return CompoundDictionaryDataNode.from(schemaNode
                    .getListElementSchemaNode(), childDataNodes);
        }
    }
}
