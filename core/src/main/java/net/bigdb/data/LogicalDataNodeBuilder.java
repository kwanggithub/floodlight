package net.bigdb.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.bigdb.BigDBException;
import net.bigdb.data.LogicalDataNode.Contribution;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.SchemaNode;

/**
 * Builder object for creating immutable logical data node instances. This
 * manages adding the contributions from the different data sources.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class LogicalDataNodeBuilder {

    /** The schema node corresponding to this logical data node. This should
     * be either a ContainerSchemaNode or ListElementSchemaNode.
     */
    private SchemaNode schemaNode;

    /** The contributions from the different data sources */
    private Map<String, LogicalDataNode.Contribution> contributions =
            new HashMap<String, LogicalDataNode.Contribution>();

    /** Should default values be returned if a child data node is not included
     * explicitly in one of the contributions.
     */
    private boolean includeDefaultValues = true;

    public LogicalDataNodeBuilder(SchemaNode schemaNode) {
        assert schemaNode != null;
        this.schemaNode = schemaNode;
    }

    public LogicalDataNodeBuilder addContribution(
            DataSource dataSource, DataNode dataNode) {
        LogicalContributionImpl contribution =
                new LogicalContributionImpl(dataSource, dataNode);
        contributions.put(dataSource.getName(), contribution);
        return this;
    }

    public LogicalDataNodeBuilder setIncludeDefaultValues(
            boolean includeDefaultValues) {
        this.includeDefaultValues = includeDefaultValues;
        return this;
    }

    public DataNode getDataNode() throws BigDBException {
        if (contributions.isEmpty())
            return DataNode.NULL;

        // FIXME: There's probably a cleaner way to do this instead of
        // hard-coding the different types of logical data nodes here.
        switch (schemaNode.getNodeType()) {
        case CONTAINER:
            return LogicalContainerDataNode.fromContributions(schemaNode,
                    contributions, includeDefaultValues);
        case LIST_ELEMENT:
            return LogicalListElementDataNode.fromContributions(schemaNode,
                    contributions, includeDefaultValues);
        case LIST:
            ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
            if (listSchemaNode.isKeyedList()) {
                return LogicalKeyedListDataNode.fromContributions(schemaNode,
                        contributions, includeDefaultValues);
            } else {
                Set<String> dataSources = schemaNode.getDataSources();
                assert dataSources.size() == 1;
                String dataSourceName = dataSources.iterator().next();
                Contribution contribution = contributions.get(dataSourceName);
                return LogicalUnkeyedListDataNode.fromContribution(schemaNode,
                        contribution, includeDefaultValues);
            }
        default:
            // There currently aren't logical wrappers for the physical
            // leaf or leaf list data nodes since they always come from a single
            // data source and don't need logical aggregation. Also, default
            // value substitution is done in the LogicalDictionaryDataNode.
            // When we support mutation operations it might make sense to have
            // logical wrappers for leaf and leaf list nodes so that we have
            // access to the schema node
            throw new BigDBException("Invalid logical data node type");
        }
    }
}
