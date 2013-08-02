package net.bigdb.data;

import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.schema.SchemaNode;

/** The list element implementation of a logical data node. Most of the real
 * work is done in LogicalDiciontaryDataNode. This class really just overrides
 * getNodeType() to return the appropriate node type.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class LogicalListElementDataNode extends LogicalDictionaryDataNode {

    public LogicalListElementDataNode(SchemaNode schemaNode,
            Map<String, Contribution> dataSourceContributions,
            boolean includeDefaultValues) throws BigDBException {
        super(schemaNode, dataSourceContributions, includeDefaultValues);
    }

    /** Factory method to build a list element logical data node from the given
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
        return new LogicalListElementDataNode(schemaNode,
                dataSourceContributions, includeDefaultValues);
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.LIST_ELEMENT;
    }
}
