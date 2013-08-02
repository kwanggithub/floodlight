package net.bigdb.data;

import java.util.Map;

import net.bigdb.schema.SchemaNode;

/** Interface for a logical data node that has methods to access the schema
 * node and the contributions from the different data sources.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface LogicalDataNode extends DataNode {

    /** Interface for a single contribution from a single data source */
    public interface Contribution {

        /** @return the data source associated with this contribution */
        public DataSource getDataSource();

        /** @return the physical data node associated with this contribution */
        public DataNode getDataNode();
    }

    /** @return the schema node corresponding to the logical data node */
    public SchemaNode getSchemaNode();

    /** @return the contributions to this logical data node */
    public Map<String, Contribution> getContributions();
}
