package org.projectfloodlight.db.data;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.DataNode.DataNodeWithPath;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.schema.Schema;
import org.projectfloodlight.db.schema.SchemaNode;

/**
 * A do-nothing concrete implementation of the DataSource interface.
 * In particular, the implementation of the data access methods all return
 * UnsupportedOperationException exceptions. Implementations of read-only
 * dynamic data sources only need to override the queryData method.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public abstract class AbstractDataSource implements DataSource {

    protected String name;
    protected boolean config;
    protected DataNodeFactory dataNodeFactory;
    protected MutationListener mutationListener;
    private State state;
    private final Schema schema;

    public AbstractDataSource(String name, boolean config, Schema schema,
            DataNodeFactory dataNodeFactory) {
        this.name = name;
        this.config = config;
        this.schema = schema;
        this.dataNodeFactory = dataNodeFactory;
        this.state = State.CREATED;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isConfig() {
        return config;
    }

    @Override
    public DataNodeFactory getDataNodeFactory() {
        return dataNodeFactory;
    }

    @Override
    public synchronized void setMutationListener(MutationListener mutationListener) {
        this.mutationListener = mutationListener;
    }

    public SchemaNode getRootSchemaNode() throws BigDBException {
        return schema.getSchemaNode(LocationPathExpression.ROOT_PATH);
    }

    @Override
    public Iterable<DataNodeWithPath> queryData(Query query,
            AuthContext authContext) throws BigDBException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void
            insertData(Query query, DataNode data, AuthContext authContext)
                    throws BigDBException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void
            replaceData(Query query, DataNode data, AuthContext authContext)
                    throws BigDBException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateData(Query query, DataNode data, AuthContext authContext)
            throws BigDBException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteData(Query query, AuthContext authContext)
            throws BigDBException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized State getState() {
        return state;
    }

    @Override
    public DataNode getRoot(AuthContext authContext, Query query)
            throws BigDBException {
        return getRoot();
    }

    @Override
    public synchronized void startup() throws BigDBException {
        if(state != State.CREATED)
            throw new IllegalStateException("Illegal state for startup: DataSource is not CREATED but "+state);

        state = State.RUNNING;
    }

    @Override
    public synchronized void shutdown() throws BigDBException {
        state = State.STOPPED;
    }

}
