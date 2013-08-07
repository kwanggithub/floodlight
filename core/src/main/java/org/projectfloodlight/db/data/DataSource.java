package org.projectfloodlight.db.data;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode.DataNodeWithPath;
import org.projectfloodlight.db.query.Query;

/**
 * The DataSource interface defines the API between the core BigDB code and
 * different backend implementations to expose and/or persistently store data.
 * DataSource adhere to the following lifecycle model:
 *
 * <pre>
 * -----------   start()  ----------- shutdown()  -----------
 * | created |  --------> | running | --------->  | stopped |
 * -----------            -----------             -----------
 * </pre>
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface DataSource {

    /**
     * Each data source must have a unique name. A node in the schema is
     * assigned to a specific data source to get/set the data and the data
     * source is identified using this name.
     *
     * @return name for the data source
     */
    public String getName();

    /**
     * @return true if this data source handles config state; false if it
     *         handles operational state
     */
    public boolean isConfig();

    /**
     * Return an implementation of the DataNodeFactory interface that is used
     * to create the data nodes that are passed to the data source in the
     * replaceData and updateData methods.
     *
     * @return factory object that creates appropriate data nodes for this
     *         data source
     */
    public DataNodeFactory getDataNodeFactory();

    public void setMutationListener(MutationListener listener) throws BigDBException;

    /**
     * Query for nodes in the tree. The data nodes returned from the query
     * shouldn't change after returning to the caller, i.e. subsequent mutating
     * operations to the tree shouldn't affect the data returned from earlier
     * queries.
     *
     * @param query
     *            query of the data node to query
     * @param authContext
     * @return the data nodes and associated paths that match the query
     * @throws BigDBException
     */
    public Iterable<DataNodeWithPath> queryData(Query query,
            AuthContext authContext) throws BigDBException;

    /**
     * Insert new list element data nodes in the list data nodes specified
     * by the input query path.
     *
     * @param query
     *            query of the data nodes to replace
     * @param data
     * @param authContext
     * @throws BigDBException
     */
    public void
            insertData(Query query, DataNode data, AuthContext authContext)
                    throws BigDBException;

    /**
     * Completely replace all data nodes in the tree that match the input
     * location path with the input data.
     *
     * @param query
     *            query of the data nodes to replace
     * @param data
     * @param authContext
     * @throws BigDBException
     */
    public void
            replaceData(Query query, DataNode data, AuthContext authContext)
                    throws BigDBException;

    /**
     * Update all data nodes in the tree that match the input query with the
     * input data.
     *
     * @param query
     *            query of the data nodes to update
     * @param data
     * @param authContext
     * @throws BigDBException
     */
    public void updateData(Query query, DataNode data, AuthContext authContext)
            throws BigDBException;

    /**
     * Delete all data nodes in the tree that match the input location path.
     *
     * @param query query of the data nodes to delete
     * @param authContext
     * @throws BigDBException
     */
    public void deleteData(Query query, AuthContext authContext)
            throws BigDBException;

    /**
     * @return the root data node for the data source
     *
     * @throws BigDBException
     */
    DataNode getRoot() throws BigDBException;

    /**
     * The the data source root data node corresponding to the specified
     * query and auth context.
     *
     * @param authContext
     * @param query
     * @return
     * @throws BigDBException
     */
    public DataNode getRoot(AuthContext authContext, Query query) throws BigDBException;

    /******** Life cycle methods *******/
    enum State { CREATED, RUNNING, STOPPED };

    /** return the current state of the DataSource */
    public State getState ();

    /** startup this data source. If the datasource needs to load data from
     *  persistent storage on startup, this is expected to happen in step
     *  startup.
     *
     *  @throws BigDBException if an error occurs during the start up of the datasource
     *  @throws IllegalStateException if this data source is not in state CREATED
     */
    public void startup() throws BigDBException;

    /** shutdown this data source. NoOP if this datasource is not currently running.
     */
    public void shutdown() throws BigDBException;



}
