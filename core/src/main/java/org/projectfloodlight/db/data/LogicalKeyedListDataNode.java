package org.projectfloodlight.db.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.schema.ListElementSchemaNode;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.UnmodifiableIterator;

/**
 * Implementation of a keyed list data node that aggregates contributions from
 * multiple data sources.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class LogicalKeyedListDataNode extends AbstractKeyedListDataNode
        implements LogicalDataNode {

    protected final static Logger logger = LoggerFactory
            .getLogger(LogicalKeyedListDataNode.class);

    /**
     * Implementation of the entry iterator that keeps track of the state of the
     * underlying iterators over the contributions from each of the data sources.
     *
     * @author rob.vaterlaus@bigswitch.com
     */
    private class LogicalKeyedListEntryIterator extends UnmodifiableIterator<KeyedListEntry> {

        /** This class keeps track of the iterator state for a single data
         * source contribution.
         */
        private class ContributionInfo {

            /** The data source for this contribution */
            DataSource dataSource;

            /** The iterator over the entries in the list data node for this
             * contribution.
             */
            Iterator<KeyedListEntry> entryIterator;

            /** The next entry to return for this contribution.
             * When we've consumed all of the entries for this contribution
             * then this is set to null.
             */
            KeyedListEntry pendingEntry;

            ContributionInfo(DataSource dataSource, Iterator<KeyedListEntry> entryIterator) {
                this.dataSource = dataSource;
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

        /** The contributions from the different data sources */
        private List<ContributionInfo> iteratorContributions;

        LogicalKeyedListEntryIterator() {
            iteratorContributions = new ArrayList<ContributionInfo>();
            try {
                for (Contribution contribution : contributions.values()) {
                    DataSource dataSource = contribution.getDataSource();
                    Iterator<KeyedListEntry> entryIterator =
                            contribution.getDataNode().getKeyedListEntries().iterator();
                    ContributionInfo iteratorContribution =
                            new ContributionInfo(dataSource, entryIterator);
                    iteratorContributions.add(iteratorContribution);
                }
            } catch (BigDBException e) {
                throw new BigDBInternalError(
                        "Error building logical keyed list entry iterator", e);
            }
        }

        @Override
        public boolean hasNext() {
            // There's more list elements available from the logical iterator
            // as long as any of the physical iterators still have more
            // elements.
            for (ContributionInfo contribution : iteratorContributions) {
                if (contribution.pendingEntry != null) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public KeyedListEntry next() {
            // First find the smallest key value across all of the
            // physical iterators
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
                throw new NoSuchElementException();

            try {
                // Create a logical data node for the list element that contains
                // the contributions from all of the data sources for the key
                // value we found above.
                ListElementSchemaNode listElementSchemaNode =
                        schemaNode.getListElementSchemaNode();
                LogicalDataNodeBuilder builder =
                        new LogicalDataNodeBuilder(listElementSchemaNode);
                builder.setIncludeDefaultValues(includeDefaultValues);
                for (ContributionInfo contribution : iteratorContributions) {
                    KeyedListEntry entry = contribution.pendingEntry;
                    if ((entry != null) && smallestKeyValue.equals(entry.getKeyValue())) {
                        builder.addContribution(contribution.dataSource,
                                contribution.pendingEntry.getDataNode());
                        contribution.updatePendingEntry();
                    }
                }
                DataNode logicalDataNode = builder.getDataNode();
                return new KeyedListEntryImpl(smallestKeyValue, logicalDataNode);
            } catch (BigDBException e) {
                throw new BigDBInternalError("Error building logical list element data node");
            }
        }
    }

    private class LogicalKeyedListEntryIterable implements Iterable<KeyedListEntry> {
        @Override
        public Iterator<KeyedListEntry> iterator() {
            return new LogicalKeyedListEntryIterator();
        }
    }

    /** The schema node corresponding to this logical list data node. */
    protected final ListSchemaNode schemaNode;

    /** The contributions from the different data sources */
    protected final Map<String, Contribution> contributions;

    /** Should default values be returned if a child data node is not included
     * explicitly in one of the contributions.
     */
    protected final boolean includeDefaultValues;

    public LogicalKeyedListDataNode(SchemaNode schemaNode,
            Map<String, Contribution> contributions,
            boolean includeDefaultValues) throws BigDBException {

        super();

        assert schemaNode != null;
        assert schemaNode instanceof ListSchemaNode;
        assert contributions != null;

        this.schemaNode = (ListSchemaNode) schemaNode;
        this.contributions = contributions;
        this.includeDefaultValues = includeDefaultValues;

        freeze();
    }

    @Override
    public SchemaNode getSchemaNode() {
        return schemaNode;
    }

    @Override
    public Map<String, Contribution> getContributions() {
        return contributions;
    }

    /** Factory method to create a logical list data node from the different
     * contributions. This is called from LogicalDataNodeBuilder.
     *
     * @param schemaNode
     * @param contributions
     * @param includeDefaultValues
     * @return
     * @throws BigDBException
     */
    public static LogicalDataNode fromContributions(SchemaNode schemaNode,
            Map<String, Contribution> contributions,
            boolean includeDefaultValues) throws BigDBException {
        return new LogicalKeyedListDataNode(schemaNode, contributions,
                includeDefaultValues);
    }

    @Override
    public DigestValue computeDigestValue() {
        // Computing the digest values for logical data nodes could be
        // expensive so for now we just don't support it.
        // We'll see if it's needed.
        return null;
    }

    @Override
    public Iterable<KeyedListEntry> getKeyedListEntries() {
        return new LogicalKeyedListEntryIterable();
    }

    @Override
    public IndexSpecifier getKeySpecifier() {
        return schemaNode.getKeySpecifier();
    }

    @Override
    public boolean hasChild(IndexValue keyValue) throws BigDBException {
        for (Contribution contribution : contributions.values()) {
            if (contribution.getDataNode().hasChild(keyValue))
                return true;
        }
        return false;
    }

    @Override
    public DataNode getChild(IndexValue keyValue) throws BigDBException {
        SchemaNode listElementSchemaNode =
                schemaNode.getListElementSchemaNode();
        // Build a logical list element data node from the contributions that
        // have data associated with the specified key value.
        LogicalDataNodeBuilder builder =
                new LogicalDataNodeBuilder(listElementSchemaNode);
        for (Contribution contribution : contributions.values()) {
            DataNode dataNode = contribution.getDataNode();
            DataNode listElementDataNode = dataNode.getChild(keyValue);
            if (!listElementDataNode.isNull()) {
                builder.addContribution(contribution.getDataSource(),
                        listElementDataNode);
            }
        }
        return builder.getDataNode();
    }
}
