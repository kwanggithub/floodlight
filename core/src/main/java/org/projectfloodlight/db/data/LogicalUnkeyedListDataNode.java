package org.projectfloodlight.db.data;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;

import com.google.common.collect.UnmodifiableIterator;

/**
 * Implementation of an unkeyed list data node. Unkeyed list data nodes must
 * always come from a single data source, because we don't have a key value
 * that lets us correlate contributions from multiple data sources. We still
 * create a logical data node for it, though, not to handle aggregation, but to
 * handle conformance to the schema.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class LogicalUnkeyedListDataNode extends AbstractUnkeyedListDataNode
        implements LogicalDataNode {

    /**
     * Iterator implementation that iterates over the list elements from the
     * single data source contribution. Mostly delegates to the underlying
     * iterator from the contribution, but also needs to wrap the data nodes
     * that are returned from the next() method in logical list elements.
     */
    private class LogicalIterator extends UnmodifiableIterator<DataNode> {

        private Iterator<DataNode> contributionIterator;

        LogicalIterator(Iterator<DataNode> contributionIterator) {
            this.contributionIterator = contributionIterator;
        }

        @Override
        public boolean hasNext() {
            return contributionIterator.hasNext();
        }

        @Override
        public DataNode next() {
            DataNode listElement = contributionIterator.next();
            SchemaNode listElementSchemaNode =
                    schemaNode.getListElementSchemaNode();
            // Build a logical list element data node with the same single
            // data source contribution as the parent logical list data node.
            LogicalDataNodeBuilder builder =
                    new LogicalDataNodeBuilder(listElementSchemaNode);
            DataSource dataSource = contribution.getDataSource();
            builder.addContribution(dataSource, listElement);
            builder.setIncludeDefaultValues(includeDefaultValues);
            try {
                return builder.getDataNode();
            } catch (BigDBException e) {
                throw new AssertionError(
                        "Unexpected error building logical list element data node");
            }
        }
    }

    /** The schema node corresponding to this logical list data node. */
    protected final ListSchemaNode schemaNode;

    /** The single contribution for the unkeyed list */
    protected Contribution contribution;

    /** Should default values be returned if a child data node is not included
     * explicitly in one of the contributions.
     */
    protected final boolean includeDefaultValues;

    public LogicalUnkeyedListDataNode(SchemaNode schemaNode,
            Contribution contribution, boolean includeDefaultValues)
                    throws BigDBException {
        super();

        assert schemaNode != null;
        assert schemaNode instanceof ListSchemaNode;
        assert contribution != null;

        this.schemaNode = (ListSchemaNode) schemaNode;
        this.contribution = contribution;
        this.includeDefaultValues = includeDefaultValues;

        freeze();
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
    public static LogicalDataNode fromContribution(SchemaNode schemaNode,
            Contribution contribution, boolean includeDefaultValues)
                    throws BigDBException {
        return new LogicalUnkeyedListDataNode(schemaNode, contribution,
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
    public SchemaNode getSchemaNode() {
        return schemaNode;
    }

    @Override
    public Map<String, Contribution> getContributions() {
        return Collections.singletonMap(contribution.getDataSource().getName(),
                    contribution);
    }

    @Override
    public int childCount() throws BigDBException {
        // Delegate to the underlying contribution
        return contribution.getDataNode().childCount();
    }

    @Override
    public boolean hasChildren() throws BigDBException {
        // Delegate to the underlying contribution
        return contribution.getDataNode().hasChildren();
    }

    @Override
    public DataNode getChild(int index) {
        // Delegate to the underlying contribution
        return contribution.getDataNode().getChild(index);
    }

    @Override
    public Iterator<DataNode> iterator() {
        return new LogicalIterator(contribution.getDataNode().iterator());
    }
}
