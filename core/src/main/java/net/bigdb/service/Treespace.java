package net.bigdb.service;

import java.io.InputStream;

import net.bigdb.BigDBException;
import net.bigdb.auth.AuthContext;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeSet;
import net.bigdb.data.DataSource;
import net.bigdb.data.MutationListener;
import net.bigdb.hook.HookRegistry;
import net.bigdb.query.Query;
import net.bigdb.schema.Schema;

/**
 * A treespace encapsulates:
 * - A schema that defines the layout of a data tree
 * - The corresponding data that has been set for the treespace. The data
 *   matches the treespace schema.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface Treespace {

    enum DataFormat {
        JSON
    };

    /**
     * Get the name of the treespace. Each treespace has a unique name.
     *
     * @return the treespace name
     */
    public String getName();

    /**
     * Get the schema for the treespace
     *
     * @return the schema object
     */
    public Schema getSchema();

    /**
     * Get the hook registry through which clients can install hooks to
     * customize BigDB query and mutation operations.
     *
     * @return the hook registry
     */
    public HookRegistry getHookRegistry();

    /**
     * Register a dynamic data source with the treespace
     *
     * @param dataSource The data source that will handle query/mutate
     *        operations for the specified subtree.
     * @throws BigDBException
     */
    public void registerDataSource(DataSource dataSource)
            throws BigDBException;

    /**
     * Register a listener object on the nodes matching the specified query.
     *
     * @param query
     * @param listener
     * @param recursive
     * @throws BigDBException
     */
    public void registerMutationListener(Query query, boolean recursive,
            MutationListener listener) throws BigDBException;

    /**
     * Unregister a previously registered listener
     *
     * @param query
     * @param listener
     * @throws BigDBException
     */
    public void unregisterMutationListener(Query query, MutationListener listener)
            throws BigDBException;

    /**
     * Look up the data corresponding to a path in the schema for the treespace.
     *
     * @param path a path into the treespace
     * @return the data node at the specified path
     * @throws BigDBException
     */
    public DataNodeSet queryData(Query query, AuthContext context)
            throws BigDBException;

    /**
     * Look up the data corresponding to a path in the schema for the treespace.
     * Return the data in the specified string format.
     *
     * @param path a path into the treespace
     * @param format the format of the data to return
     * @return the data node at the specified path serialized to the specified
     *         string format.
     * @throws BigDBException
     */
    public String queryData(Query query, DataFormat format, AuthContext context)
            throws BigDBException;

    /**
     * Insert new list element data nodes in the list data nodes specified
     * by the input query path.
     *
     * @param queryPath
     * @param data
     * @param context
     * @throws BigDBException
     */
    public void insertData(Query query, DataNode data, AuthContext context)
            throws BigDBException;

    /**
     * Insert new list element data nodes in the list data nodes specified
     * by the input query path.
     *
     * @param queryPath
     * @param format
     * @param data
     * @param context
     * @throws BigDBException
     */
    public void insertData(Query query, DataFormat format, InputStream data,
            AuthContext context)
            throws BigDBException;

    /**
     * Completely replace all data nodes in the tree that match the input query
     * path with the input data.
     *
     * @param queryPath
     * @param data
     * @throws BigDBException
     */
    public void replaceData(Query query, DataNode data, AuthContext context)
            throws BigDBException;

    /**
     * Completely replace all data nodes in the tree that match the input query
     * path with the input data.
     *
     * @param queryPath
     * @param format
     * @param data
     * @throws BigDBException
     */
    public void replaceData(Query query, DataFormat format, InputStream data,
            AuthContext context)
            throws BigDBException;

    /**
     * Update all data nodes in the tree that match the input query
     * path with the input data.
     *
     * @param queryPath
     * @param data
     * @throws BigDBException
     */
    public void updateData(Query query, DataNode data, AuthContext context)
            throws BigDBException;

    /**
     * Update all data nodes in the tree that match the input query
     * path with the input data.
     *
     * @param queryPath
     * @param format
     * @param data
     * @throws BigDBException
     */
    public void updateData(Query query, DataFormat format, InputStream data,
            AuthContext context)
            throws BigDBException;


    /**
     * Delete all data nodes in the tree that match the input query.
     *
     * @param queryPath
     * @throws BigDBException
     */
    public void deleteData(Query query, AuthContext context) throws BigDBException;

    void startup() throws BigDBException;
}
