package org.projectfloodlight.db.data;

import java.util.Collection;
import java.util.Iterator;

import org.projectfloodlight.db.data.DataNode.DataNodeWithPath;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;

import com.google.common.collect.ImmutableList;

/**
 * An Iterable<DataNode> implementation that wraps the elements being iterated
 * over in an appropriate Select*DataNode instance. The real work is done in
 * the SelectDataNodeIterator class. This class just creates an instance of
 * that class.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public final class SelectDataNodeIterable implements Iterable<DataNodeWithPath> {

    /**
     * The Schema node corresponding the type of the data node over which
     * we're iterating
     */
    private final SchemaNode schemaNode;

    /**
     * The selected paths that should be visible in the data nodes over which
     * we're iterating
     */
    private final Collection<LocationPathExpression> selectedPaths;

    /**
     * The underlying iterable source for the nodes we're wrapping in select
     * data nodes
     */
    private final Iterable<DataNodeWithPath> sourceIterable;

    public SelectDataNodeIterable(SchemaNode schemaNode,
            Collection<LocationPathExpression> selectedPaths,
            Iterable<DataNodeWithPath> sourceIterable) {
        assert schemaNode != null;
        if (schemaNode.getNodeType() == SchemaNode.NodeType.LIST) {
            ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
            assert listSchemaNode.getKeySpecifier() != null;
        }
        this.schemaNode = schemaNode;
        this.selectedPaths = ImmutableList.copyOf(selectedPaths);
        this.sourceIterable = sourceIterable;
    }


    @Override
    public Iterator<DataNodeWithPath> iterator() {
        return new SelectDataNodeIterator(schemaNode, selectedPaths,
                sourceIterable.iterator());
    }
}
