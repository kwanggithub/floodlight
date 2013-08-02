package net.bigdb.data;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;

import net.bigdb.BigDBException;
import net.bigdb.query.Step;
import net.bigdb.schema.AggregateSchemaNode;
import net.bigdb.schema.InvalidSchemaTypeException;
import net.bigdb.schema.LeafSchemaNode;
import net.bigdb.schema.ListElementSchemaNode;
import net.bigdb.schema.SchemaNode;

/**
 * LogicalDictionaryDataNode is an implementation of a dictionary data node
 * (i.e. either a container or a list element data node) that aggregates
 * contributions from multiple data sources. It is schema-aware, so it catches
 * operations that violate the constraints of the schema and also handles
 * substituting default values for the child data nodes if they aren't
 * specified in any of the contributions.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class LogicalDictionaryDataNode extends AbstractDictionaryDataNode
        implements LogicalDataNode {

    /** The schema node corresponding to this logical data node. This should
     * be either a ContainerSchemaNode or ListElementSchemaNode.
     */
    private final AggregateSchemaNode schemaNode;

    /** The contributions from the different data sources */
    private final Map<String, Contribution> contributions;

    /** Should default values be returned if a child data node is not included
     * explicitly in one of the contributions.
     */
    private final boolean includeDefaultValues;

    protected LogicalDictionaryDataNode(SchemaNode schemaNode,
            Map<String, Contribution> contributions,
            boolean includeDefaultValues) throws BigDBException {

        super();

        assert schemaNode != null;
        assert schemaNode instanceof AggregateSchemaNode;
        assert contributions != null;

        this.schemaNode = (AggregateSchemaNode) schemaNode;
        this.contributions = contributions;
        this.includeDefaultValues = includeDefaultValues;

        freeze();
    }

    @Override
    public DigestValue computeDigestValue() {
        // Computing the digest values for logical data nodes would be
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
        return contributions;
    }

    @Override
    public DataNode getChild(Step step) throws BigDBException {

        String name = step.getName();

        // Get the schema node for the requested child. If the name argument
        // doesn't map to a child that's defined in the schema, then this will
        // throw a SchemaNodeNotFoundException.
        SchemaNode childSchemaNode = schemaNode.getChildSchemaNode(name);
        Set<String> dataSources = childSchemaNode.getDataSources();

        switch (childSchemaNode.getNodeType()) {
        case CONTAINER:
        case LIST:
            Set<String> dataSourcesWithDefaultContent =
                    childSchemaNode.getDataSourcesWithDefaultContent();
            // Build a logical data node to wrap the contributions from
            // different data sources for aggregate node types
            LogicalDataNodeBuilder builder =
                    new LogicalDataNodeBuilder(childSchemaNode);
            for (String dataSourceName : dataSources) {
                Contribution contribution = contributions.get(dataSourceName);
                if (contribution != null) {
                    DataSource dataSource = contribution.getDataSource();
                    DataNode dataNode = contribution.getDataNode();
                    DataNode childDataNode = dataNode.getChild(step);
                    if (childDataNode.isNull() &&
                            dataSourcesWithDefaultContent.contains(dataSource.getName())) {
                        assert childSchemaNode.getNodeType() == SchemaNode.NodeType.CONTAINER;
                        childDataNode =
                                dataSource.getDataNodeFactory()
                                        .createContainerDataNode(false, null);
                    }
                    if (!childDataNode.isNull()) {
                        builder.addContribution(dataSource, childDataNode);
                    }
                }
            }
            return builder.getDataNode();
        case LEAF:
        case LEAF_LIST:
            // Leaf and leaf list nodes always map to a single data source, so
            // we don't need to wrap them in a logical data node to handle
            // aggregating the contributions from multiple data sources. So
            // we just return the physical data node directly.

            // Check if the requested child is a key node in a list element.
            // In that case the key values must be present in all contributions,
            // so we can get it from any one of them, i.e. the first one.
            // In that case it doesn't work to get it from the data source
            // that's specified in the schema, since that data source may not
            // be making a contribution to the data, so if we only tried to get
            // it from that data source we may not populate the key field.
            // FIXME: This code is a little kludgy. Should come up with a better
            // way to handle this case.
            boolean isKey = false;
            if (schemaNode.getNodeType() == SchemaNode.NodeType.LIST_ELEMENT) {
                ListElementSchemaNode listElementSchemaNode =
                        (ListElementSchemaNode) schemaNode;
                isKey = (listElementSchemaNode.getKeyNodeNames().contains(name));
            }

            Contribution contribution;
            if (isKey) {
                contribution = contributions.values().iterator().next();
            } else {
                assert dataSources.size() == 1;
                String dataSourceName = dataSources.iterator().next();
                contribution = contributions.get(dataSourceName);
            }

            if (contribution == null)
                return DataNode.NULL;

            DataNode dataNode = contribution.getDataNode();
            DataNode childDataNode = dataNode.getChild(name);

            // Optionally return default values for leaf nodes if there was
            // no explicit value for the requested child
            if (childDataNode.isNull() && includeDefaultValues &&
                    (childSchemaNode.getNodeType() == SchemaNode.NodeType.LEAF)) {
                LeafSchemaNode leafSchemaNode =
                        (LeafSchemaNode) childSchemaNode;
                childDataNode = leafSchemaNode.getDefaultValue();
                // FIXME: Ideally the default value would already be
                // DataNode.NULL instead of null, but that currently would
                // break the suppression of null default values when
                // serializing the schema info, so we do the conversion here.
                if (childDataNode == null)
                    childDataNode = DataNode.NULL;
            }
            return childDataNode;
        default:
            throw new InvalidSchemaTypeException();
        }
    }

    @Override
    public Set<String> getAllChildNames() {
        Set<String> result = new TreeSet<String>();
        for (SortedMap.Entry<String, SchemaNode> entry : schemaNode
                .getChildNodes().entrySet()) {
            SchemaNode childSchemaNode = entry.getValue();
            boolean cascade =
                    childSchemaNode.getBooleanAttributeValue(
                            SchemaNode.CASCADE_ATTRIBUTE_NAME, true);
            if (cascade) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
}
