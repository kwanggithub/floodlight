package org.projectfloodlight.db.data;

import java.util.Collection;
import java.util.Iterator;

import org.projectfloodlight.db.data.AbstractDataNode.DataNodeWithPathImpl;
import org.projectfloodlight.db.data.DataNode.DataNodeWithPath;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;

/**
 * A data node iterator that wraps the elements being iterated over in an
 * appropriate Select*DataNode instance. This is used to apply the selected
 * path in a query to the query results from evaluating the base path of the
 * query.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public final class SelectDataNodeIterator extends UnmodifiableIterator<DataNodeWithPath> {

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
     * The underlying iterator source for the nodes we're wrapping in select
     * data nodes
     */
    private final Iterator<DataNodeWithPath> sourceIterator;

    public SelectDataNodeIterator(SchemaNode schemaNode,
            Collection<LocationPathExpression> selectedPaths,
            Iterator<DataNodeWithPath> sourceIterator) {
        assert schemaNode != null;
        if (schemaNode.getNodeType() == SchemaNode.NodeType.LIST) {
            ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
            assert listSchemaNode.getKeySpecifier() != null;
        }
        this.schemaNode = schemaNode;
        this.selectedPaths = ImmutableList.copyOf(selectedPaths);
        this.sourceIterator = sourceIterator;
    }

    @Override
    public boolean hasNext() {
        return sourceIterator.hasNext();
    }

    @Override
    public DataNodeWithPath next() {
        DataNodeWithPath dataNodeWithPath = sourceIterator.next();
        DataNode selectDataNode;
        switch (schemaNode.getNodeType()) {
        case CONTAINER:
            selectDataNode = new SelectDictionaryDataNode(schemaNode,
                    dataNodeWithPath.getDataNode(), selectedPaths);
            break;
        case LIST:
            ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
            selectDataNode = new SelectDictionaryDataNode(
                    listSchemaNode.getListElementSchemaNode(),
                    dataNodeWithPath.getDataNode(), selectedPaths);
            break;
        default:
            return dataNodeWithPath;
        }
        return new DataNodeWithPathImpl(dataNodeWithPath.getPath(), selectDataNode);
    }
}
