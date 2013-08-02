package net.bigdb.schema;

import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.query.Query;

public interface Schema {
    
    /**
     * Get all of the modules in the schema.
     * @return the map of all the modules keyed by the module identifier
     */
    public Map<ModuleIdentifier,Module> getModules();
    
    /**
     * Look up a module
     * @param moduleId the ID of the module to look up
     * @return the module
     */
    public Module getModule(ModuleIdentifier moduleId);
    
    /**
     * Look up a node in the schema tree
     * @param path a path to a node in the schema tree (use null for the root)
     * @return the node in the schema tree
     * @throws BigDBException
     */
    public SchemaNode getSchemaNode(LocationPathExpression path) throws BigDBException;

    
    /**
     * Determines whether or not the query returns a list of data nodes as
     * opposed to a single data node. It will be a list in the case where
     * there's a predicate on a list node followed by a partial path identifying
     * a leaf node of an element of an element in the list. Note that this
     * is different from when returning a filtered list node. In that case
     * a single data node (of type LIST) is returned.
     * @param query
     * @return
     * @throws BigDBException
     */
    public boolean isListQuery(Query query) throws BigDBException;
}
