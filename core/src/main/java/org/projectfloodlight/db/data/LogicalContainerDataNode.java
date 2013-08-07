package org.projectfloodlight.db.data;

import java.util.Map;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.schema.SchemaNode;

/** The container implementation of a logical data node. Most of the real
 * work is done in LogicalDiciontaryDataNode. This class really just overrides
 * getNodeType() to return the appropriate node type.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class LogicalContainerDataNode extends LogicalDictionaryDataNode {

    LogicalContainerDataNode(SchemaNode schemaNode,
            Map<String, Contribution> dataSourceContributions,
            boolean includeDefaultValues) throws BigDBException {
        super(schemaNode, dataSourceContributions, includeDefaultValues);
    }

    /** Factory method to build a container logical data node from the given
     * data source contributions.
     *
     * @param schemaNode
     * @param dataSourceContributions
     * @param includeDefaultValues
     * @return
     * @throws BigDBException
     */
    public static LogicalDataNode fromContributions(
            SchemaNode schemaNode,
            Map<String, Contribution> dataSourceContributions,
            boolean includeDefaultValues) throws BigDBException {
        return new LogicalContainerDataNode(schemaNode,
                dataSourceContributions, includeDefaultValues);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.CONTAINER;
    }
}
