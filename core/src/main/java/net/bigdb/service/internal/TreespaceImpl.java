package net.bigdb.service.internal;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.bigdb.BigDBException;
import net.bigdb.auth.AuthContext;
import net.bigdb.auth.AuthorizationException;
import net.bigdb.config.DataSourceConfig;
import net.bigdb.config.DataSourceMappingConfig;
import net.bigdb.config.ModuleConfig;
import net.bigdb.config.ModuleSearchPathConfig;
import net.bigdb.config.TreespaceConfig;
import net.bigdb.data.AbstractDataNode;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNode.DataNodeWithPath;
import net.bigdb.data.DataNode.DictionaryEntry;
import net.bigdb.data.DataNode.KeyedListEntry;
import net.bigdb.data.DataNodeSerializationException;
import net.bigdb.data.DataNodeSet;
import net.bigdb.data.DataNodeUtilities;
import net.bigdb.data.DataSource;
import net.bigdb.data.DataSourceException;
import net.bigdb.data.DataSourceMapping;
import net.bigdb.data.FilterDictionaryDataNode;
import net.bigdb.data.IndexValue;
import net.bigdb.data.LogicalDataNodeBuilder;
import net.bigdb.data.MutationListener;
import net.bigdb.data.SelectDataNodeIterable;
import net.bigdb.data.TransactionalDataSource;
import net.bigdb.data.TreespaceAware;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.AuthorizationHook;
import net.bigdb.hook.FilterHook;
import net.bigdb.hook.HookRegistry;
import net.bigdb.hook.ValidationHook;
import net.bigdb.hook.WatchHook;
import net.bigdb.hook.WatchHookContextImpl;
import net.bigdb.hook.internal.AuthorizationHookContextImpl;
import net.bigdb.hook.internal.HookRegistryImpl;
import net.bigdb.hook.internal.ValidationHookContextImpl;
import net.bigdb.query.Query;
import net.bigdb.schema.AbstractSchemaNodeVisitor;
import net.bigdb.schema.AggregateSchemaNode;
import net.bigdb.schema.LeafListSchemaNode;
import net.bigdb.schema.LeafSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.ModuleIdentifier;
import net.bigdb.schema.ReferenceSchemaNode;
import net.bigdb.schema.Schema;
import net.bigdb.schema.SchemaNode;
import net.bigdb.schema.SchemaNodeVisitor;
import net.bigdb.schema.TypedefSchemaNode;
import net.bigdb.schema.UsesSchemaNode;
import net.bigdb.schema.ValidationException;
import net.bigdb.schema.internal.ModuleImpl;
import net.bigdb.schema.internal.SchemaImpl;
import net.bigdb.service.BigDBOperation;
import net.bigdb.service.Service;
import net.bigdb.service.Treespace;
import net.bigdb.util.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value="UWF_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD")
public class TreespaceImpl implements Treespace, MutationListener,
        TransactionalDataSource.PreCommitListener,
        TransactionalDataSource.PostCommitListener {

    static enum State { STOPPED, RUNNING }
    // private static final boolean ALLOW_NULL_AUTHORIZATION = true;

    protected final static Logger logger = LoggerFactory
            .getLogger(TreespaceImpl.class);

    /** The parent service for the treespace */
    protected Service service;

    /**
     * The configuration info. We really only need most of this info while we're
     * loading the treespace, but it's useful to have access to this via the
     * treespace reference while loading so we make it available here.
     */
    protected TreespaceConfig config;

    /** The schema for the treespace */
    protected SchemaImpl schema;

    /** The configured data sources, indexed by the name of the data source */
    protected Map<String, DataSource> dataSources;

    /** The hook registry for client customization of BigDB behavior */
    protected HookRegistry hookRegistry;

    /**
     * The lock is really a lock for query/mutation operations and not the data
     * members of the treespace. The data source implementation (e.g.
     * MemoryDataSource) can't handle concurrent mutation operations. If we
     * handled synchronization on a per-data-source level, then we could get
     * unpredictable interleavings of calls to the different data sources, which
     * could cause problems. So for now, we are conservative and don't allow
     * concurrent mutation operations. I think the biggest concern with this
     * currently is that all read operations are blocked while there any
     * mutation in progress. Need to optimize this eventually.
     */
    protected ReadWriteLock lock = new ReentrantReadWriteLock();

    private static class MutationListenerInfo {

        public Query query;
        public boolean recursive;
        public MutationListener mutationListener;

        public MutationListenerInfo(Query query, boolean recursive,
                MutationListener mutationListener) {
            this.query = query;
            this.recursive = recursive;
            this.mutationListener = mutationListener;
        }
    }

    protected Map<SchemaNode, List<MutationListenerInfo>> mutationListenerMap =
            new HashMap<SchemaNode, List<MutationListenerInfo>>();

    private DataNodeJsonHandler jsonHandler;

    private State state;

    public TreespaceImpl(Service service, TreespaceConfig config)
            throws BigDBException {
        assert config != null;

        this.service = service;
        this.config = config;
        this.schema = new SchemaImpl();
        this.dataSources = new HashMap<String, DataSource>();
        this.hookRegistry = new HookRegistryImpl();
        this.state = State.STOPPED;
        configure();
    }

    @Override
    public String getName() {
        return config.name;
    }

    public TreespaceConfig getConfig() {
        return config;
    }

    @Override
    public Schema getSchema() {
        return schema;
    }

    @Override
    public HookRegistry getHookRegistry() {
        return hookRegistry;
    }

    @Override
    public void registerMutationListener(Query query, boolean recursive,
            MutationListener mutationListener) throws BigDBException {
        MutationListenerInfo info =
                new MutationListenerInfo(query, recursive, mutationListener);
        SchemaNode schemaNode = schema.getSchemaNode(query.getBasePath());
        if (schemaNode == null) {
            // FIXME: define exception class for this
            throw new BigDBException("Invalid listener query");
        }

        synchronized (mutationListenerMap) {
            List<MutationListenerInfo> listenerInfoList =
                    mutationListenerMap.get(schemaNode);
            if (listenerInfoList == null) {
                listenerInfoList = new ArrayList<MutationListenerInfo>();
                mutationListenerMap.put(schemaNode, listenerInfoList);
            }
            listenerInfoList.add(info);
        }
    }

    @Override
    public void unregisterMutationListener(Query query,
            MutationListener mutationListener) throws BigDBException {
        SchemaNode schemaNode = schema.getSchemaNode(query.getBasePath());
        if (schemaNode == null) {
            // FIXME: define exception class for this
            throw new BigDBException("Invalid listener query");
        }
        synchronized (mutationListenerMap) {
            List<MutationListenerInfo> listenerInfoList =
                    mutationListenerMap.get(schemaNode);
            Iterator<MutationListenerInfo> iterator =
                    listenerInfoList.iterator();
            while (iterator.hasNext()) {
                MutationListenerInfo listenerInfo = iterator.next();
                if (listenerInfo.query.equals(query) &&
                        (listenerInfo.mutationListener == mutationListener)) {
                    iterator.remove();
                    break;
                }
            }
        }
    }

    // FIXME: There's a fair bit of code duplication in the authorize and
    // validate functions below. Should clean this up and probably use some
    // sort of template method pattern so that there's less code duplication.

    private AuthorizationHook.Result authorize(
            AuthorizationHookContextImpl hookContext,
            LocationPathExpression locationPath, SchemaNode schemaNode,
            DataNode oldDataNode, DataNode newDataNode, DataNode writtenDataNodes)
            throws BigDBException {

        if(logger.isTraceEnabled())
            logger.trace("authorize for locationPath="+locationPath );

        // If authorization is disabled then always return ACCEPT
        if (service.getAuthService() == null) {
            if(logger.isTraceEnabled())
                logger.trace("authorize: auth service disabled - returning ACCEPT");

            return AuthorizationHook.Result.ACCEPT;
        }

        // Check for a pre-authorization decision. If the preauthorize call
        // returns an ACCEPT or REJECT decision then bypass authorizing the
        // child nodes.
        boolean isList = schemaNode.getNodeType() == SchemaNode.NodeType.LIST;
        List<AuthorizationHook> preauthorizationHooks =
                hookRegistry.getAuthorizationHooks(locationPath,
                        hookContext.getOperation(),
                        AuthorizationHook.Stage.PREAUTHORIZATION, isList);
        for (AuthorizationHook authorizationHook : preauthorizationHooks) {
            hookContext.setHookInfo(locationPath, schemaNode, oldDataNode,
                    newDataNode, writtenDataNodes);
            hookContext.setStage(AuthorizationHook.Stage.PREAUTHORIZATION);
            AuthorizationHook.Result hookResult =
                    authorizationHook.authorize(hookContext);
            if (hookResult.getDecision() != AuthorizationHook.Decision.UNDECIDED) {
                if (logger.isTraceEnabled())
                    logger.trace("authorize: pre-authorization decision from " + authorizationHook + ": " + hookResult);
                return hookResult;
            }
        }

        AuthorizationHook.Result result = AuthorizationHook.Result.UNDECIDED;

        // Authorize the children
        if (writtenDataNodes.isDictionary()) {
            Iterator<DictionaryEntry> entryIterator =
                    writtenDataNodes.getDictionaryEntries().iterator();
            if (entryIterator.hasNext()) {
                List<String> keyNames;
                if (writtenDataNodes.getNodeType() == DataNode.NodeType.LIST_ELEMENT) {
                    ListSchemaNode listSchemaNode = (ListSchemaNode)
                            schemaNode.getParentSchemaNode();
                    keyNames = listSchemaNode.getKeyNodeNames();
                } else {
                    keyNames = Collections.<String>emptyList();
                }
                AggregateSchemaNode aggregateSchemaNode =
                        (AggregateSchemaNode) schemaNode;
                result = AuthorizationHook.Result.ACCEPT;
                while (entryIterator.hasNext()) {
                    DictionaryEntry entry = entryIterator.next();
                    String name = entry.getName();
                    if (keyNames.contains(name))
                        continue;
                    DataNode writtenChildDataNode = entry.getDataNode();
                    DataNode oldChildDataNode = oldDataNode.getChild(name);
                    DataNode newChildDataNode = newDataNode.getChild(name);
                    SchemaNode childSchemaNode =
                            aggregateSchemaNode.getChildSchemaNode(name);
                    LocationPathExpression childLocationPath =
                            locationPath.getChildLocationPath(name);
                    AuthorizationHook.Result childResult =
                            authorize(hookContext, childLocationPath,
                                    childSchemaNode, oldChildDataNode,
                                    newChildDataNode, writtenChildDataNode);
                    switch (childResult.getDecision()) {
                    case REJECT:
                        // If any of the children return a REJECT decision then
                        // that means the parent is rejected too, so we can return
                        // immediately
                        return childResult;
                    case UNDECIDED:
                        // If any of the children return an UNDECIDED decision then
                        // the tentative result from the children for the parent
                        // node is at best UNDECIDED, i.e. not ACCEPT, but
                        // possibly REJECT. In this case we continue iterating
                        // over the children though in case one of the other
                        // children returns a REJECT decision.
                        result = childResult;
                        break;
                    case ACCEPT:
                        // An ACCEPT result from the child doesn't override an
                        // existing UNDECIDED result, so we just continue iterating
                        break;
                    }
                }
            }
        } else if (writtenDataNodes.isKeyedList()) {
            Iterator<KeyedListEntry> entryIterator =
                    writtenDataNodes.getKeyedListEntries().iterator();
            if (entryIterator.hasNext()) {
                ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
                SchemaNode childSchemaNode =
                        listSchemaNode.getListElementSchemaNode();
                result = AuthorizationHook.Result.ACCEPT;
                while (entryIterator.hasNext()) {
                    KeyedListEntry entry = entryIterator.next();
                    IndexValue keyValue = entry.getKeyValue();
                    if(logger.isTraceEnabled())
                        logger.trace("authorize: keyValue " + keyValue +
                                " iterator " + entryIterator);

                    DataNode writtenChildDataNode = entry.getDataNode();
                    DataNode oldChildDataNode = oldDataNode.getChild(keyValue);
                    DataNode newChildDataNode = newDataNode.getChild(keyValue);
                    LocationPathExpression childLocationPath =
                            DataNodeUtilities.getListElementLocationPath(
                                    locationPath, keyValue);
                    AuthorizationHook.Result childResult =
                            authorize(hookContext, childLocationPath,
                                    childSchemaNode, oldChildDataNode,
                                    newChildDataNode, writtenChildDataNode);
                    if(logger.isTraceEnabled())
                        logger.trace("authorize: child result for " +
                                childLocationPath+ ": " + childResult);

                    switch (childResult.getDecision()) {
                    case REJECT:
                        // If any of the children return a REJECT decision then
                        // that means the parent is rejected too, so we can return
                        // immediately
                        return childResult;
                    case UNDECIDED:
                        // If any of the children return an UNDECIDED decision then
                        // the tentative result from the children for the parent
                        // node is at best UNDECIDED, i.e. not ACCEPT, but
                        // possibly REJECT. In this case we continue iterating
                        // over the children though in case one of the other
                        // children returns a REJECT decision.
                        result = childResult;
                        break;
                    case ACCEPT:
                        // An ACCEPT result from the child doesn't override an
                        // existing UNDECIDED result, so we just continue iterating
                        break;
                    }
                }
            }
        }

        // Check for an authorization decision from any authorizers
        // registered for this node.
        List<AuthorizationHook> authorizationHooks =
                hookRegistry.getAuthorizationHooks(locationPath,
                        hookContext.getOperation(),
                        AuthorizationHook.Stage.AUTHORIZATION, isList);

        if (logger.isTraceEnabled())
            logger.trace("authorize: local hooks for " + locationPath + ": " + authorizationHooks);

        for (AuthorizationHook authorizationHook : authorizationHooks) {
            hookContext.setHookInfo(locationPath, schemaNode, oldDataNode,
                    newDataNode, writtenDataNodes);
            hookContext.setStage(AuthorizationHook.Stage.AUTHORIZATION);
            AuthorizationHook.Result hookResult =
                    authorizationHook.authorize(hookContext);

            if (logger.isTraceEnabled())
                logger.trace("authorize: hookResult from " + authorizationHook + ": " + hookResult);

            if (hookResult.getDecision() != AuthorizationHook.Decision.UNDECIDED) {
                result = hookResult;
                break;
            }
        }

        return result;
    }

    public void authorize(DataNode oldRootDataNode, DataNode newRootDataNode,
            DataNode writtenRootDataNode, AuthContext authContext)
                    throws BigDBException {

        AuthorizationHookContextImpl authorizationHookContext =
                new AuthorizationHookContextImpl(
                        AuthorizationHook.Operation.MUTATION, oldRootDataNode,
                        newRootDataNode, writtenRootDataNode, authContext);
        SchemaNode rootSchemaNode = schema.getSchemaRoot();
        AuthorizationHook.Result authorizationResult =
                authorize(authorizationHookContext,
                        LocationPathExpression.ROOT_PATH, rootSchemaNode,
                        oldRootDataNode, newRootDataNode, writtenRootDataNode);
        if (authorizationResult.getDecision() != AuthorizationHook.Decision.ACCEPT)
            throw new AuthorizationException(authorizationResult.toString());
    }

    private ValidationHook.Result validate(
            ValidationHookContextImpl hookContext,
            LocationPathExpression locationPath,  SchemaNode schemaNode,
            DataNode oldDataNode, DataNode newDataNode, DataNode dataNodeDiffs)
                    throws BigDBException {

        ValidationHook.Result result = ValidationHook.Result.VALID;

        // Validate the children
        if (dataNodeDiffs.isDictionary()) {
            AggregateSchemaNode aggregateSchemaNode =
                    (AggregateSchemaNode) schemaNode;
            for (DictionaryEntry entry : dataNodeDiffs.getDictionaryEntries()) {
                String name = entry.getName();
                DataNode childDataNodeDiffs = entry.getDataNode();
                DataNode oldChildDataNode = oldDataNode.getChild(name);
                DataNode newChildDataNode = newDataNode.getChild(name);
                SchemaNode childSchemaNode =
                        aggregateSchemaNode.getChildSchemaNode(name);
                LocationPathExpression childLocationPath =
                        locationPath.getChildLocationPath(name);
                result =
                        validate(hookContext, childLocationPath, childSchemaNode,
                                oldChildDataNode, newChildDataNode,
                                childDataNodeDiffs);
                if (result.getDecision() == ValidationHook.Decision.INVALID)
                    break;
            }
        } else if (dataNodeDiffs.isKeyedList()) {
            ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
            SchemaNode childSchemaNode =
                    listSchemaNode.getListElementSchemaNode();
            for (KeyedListEntry entry : dataNodeDiffs.getKeyedListEntries()) {
                IndexValue keyValue = entry.getKeyValue();
                DataNode childDataNodeDiffs = entry.getDataNode();
                // If childDataNodeDiffs is NULL, then that means the list
                // element was deleted. In that case there's no data to
                // validate, so we don't need to make the recursive
                // validation call.
                if (childDataNodeDiffs.isNull())
                    continue;
                DataNode oldChildDataNode = oldDataNode.getChild(keyValue);
                DataNode newChildDataNode = newDataNode.getChild(keyValue);
                LocationPathExpression childLocationPath =
                        DataNodeUtilities.getListElementLocationPath(
                                locationPath, keyValue);
                result =
                        validate(hookContext, childLocationPath, childSchemaNode,
                                oldChildDataNode, newChildDataNode,
                                childDataNodeDiffs);
                if (result.getDecision() == ValidationHook.Decision.INVALID)
                    break;
            }
        }

        if (result.getDecision() == ValidationHook.Decision.VALID) {
            // If all of the children are valid, then call the validation
            // hooks for this node.
            boolean isList = schemaNode.getNodeType() == SchemaNode.NodeType.LIST;
            List<ValidationHook> validationHooks =
                    hookRegistry.getValidationHooks(locationPath, isList);
            for (ValidationHook validationHook : validationHooks) {
                hookContext.setHookInfo(locationPath, isList, newDataNode);
                result = validationHook.validate(hookContext);
                if (result.getDecision() == ValidationHook.Decision.INVALID)
                    break;
            }
        }

        return result;

    }

    @Override
    public void preCommit(DataNode oldRootDataNode, DataNode newRootDataNode,
            DataNode rootDataNodeDiffs) throws BigDBException {

        // Call validation hooks
        ValidationHookContextImpl validationHookContext =
                new ValidationHookContextImpl(newRootDataNode);
        ValidationHook.Result validationResult =
                validate(validationHookContext, LocationPathExpression.ROOT_PATH,
                        schema.getSchemaRoot(), oldRootDataNode,
                        newRootDataNode, rootDataNodeDiffs);
        if (validationResult.getDecision() == ValidationHook.Decision.INVALID)
            throw new ValidationException(validationResult.getMessage());
    }

    private void watch(WatchHookContextImpl hookContext,
            LocationPathExpression locationPath,  SchemaNode schemaNode,
            DataNode oldDataNode, DataNode newDataNode, DataNode dataNodeDiffs)
                    throws BigDBException {
        // Invoke watch recursively for the child data nodes
        // FIXME: Really need to refactor this code. This code is duplicated
        // across the authorize, validate, and watch functions.
        if (dataNodeDiffs.isDictionary()) {
            AggregateSchemaNode aggregateSchemaNode =
                    (AggregateSchemaNode) schemaNode;
            for (DictionaryEntry entry : dataNodeDiffs.getDictionaryEntries()) {
                String name = entry.getName();
                DataNode childDataNodeDiffs = entry.getDataNode();
                DataNode oldChildDataNode = oldDataNode.getChild(name);
                DataNode newChildDataNode = newDataNode.getChild(name);
                SchemaNode childSchemaNode =
                        aggregateSchemaNode.getChildSchemaNode(name);
                LocationPathExpression childLocationPath =
                        locationPath.getChildLocationPath(name);
                watch(hookContext, childLocationPath, childSchemaNode,
                        oldChildDataNode, newChildDataNode, childDataNodeDiffs);
            }
        } else if (dataNodeDiffs.isKeyedList()) {
            ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
            SchemaNode childSchemaNode =
                    listSchemaNode.getListElementSchemaNode();
            for (KeyedListEntry entry : dataNodeDiffs.getKeyedListEntries()) {
                IndexValue keyValue = entry.getKeyValue();
                DataNode childDataNodeDiffs = entry.getDataNode();
                DataNode oldChildDataNode = oldDataNode.getChild(keyValue);
                DataNode newChildDataNode = newDataNode.getChild(keyValue);
                LocationPathExpression childLocationPath =
                        DataNodeUtilities.getListElementLocationPath(
                                locationPath, keyValue);
                watch(hookContext, childLocationPath, childSchemaNode,
                        oldChildDataNode, newChildDataNode, childDataNodeDiffs);
            }
        }

        // Call the watch hooks for this node.
        boolean isList = schemaNode.getNodeType() == SchemaNode.NodeType.LIST;
        List<WatchHook> watchHooks =
                hookRegistry.getWatchHooks(locationPath, isList);
        for (WatchHook watchHook : watchHooks) {
            hookContext.setHookInfo(locationPath, schemaNode, oldDataNode,
                    newDataNode, dataNodeDiffs);
            watchHook.watch(hookContext);
        }
    }

    @Override
    public void postCommit(DataNode oldRootDataNode, DataNode newRootDataNode,
            DataNode rootDataNodeDiffs) throws BigDBException {

        // Call watch hooks
        WatchHookContextImpl watchHookContext =
                new WatchHookContextImpl(oldRootDataNode, newRootDataNode,
                        rootDataNodeDiffs);
        watch(watchHookContext, LocationPathExpression.ROOT_PATH,
                schema.getSchemaRoot(), oldRootDataNode, newRootDataNode,
                rootDataNodeDiffs);
    }

    @Override
    public void dataNodesMutated(Set<Query> mutatedNodes, Operation operation,
            AuthContext authContext) throws BigDBException {
        Map<MutationListener, Set<Query>> listenerMap =
                new HashMap<MutationListener, Set<Query>>();
        for (Query mutatedNode : mutatedNodes) {
            SchemaNode mutatedSchemaNode =
                    schema.getSchemaNode(mutatedNode.getBasePath());
            // FIXME: This isn't very efficient!
            for (Map.Entry<SchemaNode, List<MutationListenerInfo>> entry :
                    mutationListenerMap.entrySet()) {
                SchemaNode listenerSchemaNode = entry.getKey();
                List<MutationListenerInfo> listenerInfoList = entry.getValue();
                boolean isChild =
                        listenerSchemaNode
                                .isAncestorSchemaNode(mutatedSchemaNode);
                boolean isAncestor =
                        mutatedSchemaNode
                                .isAncestorSchemaNode(listenerSchemaNode);
                if ((mutatedSchemaNode == listenerSchemaNode) || isChild ||
                        isAncestor) {
                    for (MutationListenerInfo info : listenerInfoList) {
                        // FIXME: Eventually should also match against the
                        // listener query object.
                        if ((mutatedSchemaNode == listenerSchemaNode) ||
                                isChild || info.recursive) {
                            Set<Query> listenerQuerySet =
                                    listenerMap.get(info.mutationListener);
                            if (listenerQuerySet == null) {
                                listenerQuerySet = new HashSet<Query>();
                                listenerMap.put(info.mutationListener,
                                        listenerQuerySet);
                            }
                            listenerQuerySet.add(mutatedNode);
                        }
                    }
                }
            }
        }

        for (Map.Entry<MutationListener, Set<Query>> entry : listenerMap
                .entrySet()) {
            MutationListener listener = entry.getKey();
            Set<Query> listenerMutatedNodes = entry.getValue();
            listener.dataNodesMutated(listenerMutatedNodes, operation, authContext);
        }
    }

    @Override
    public synchronized void registerDataSource(DataSource dataSource) 
            throws BigDBException {
        String dataSourceName = dataSource.getName();
        dataSources.put(dataSourceName, dataSource);

        if(dataSource instanceof TreespaceAware)
            ((TreespaceAware) dataSource).setTreespace(this);

        dataSource.setMutationListener(this);
        if (dataSource instanceof TransactionalDataSource) {
            TransactionalDataSource transactionalDataSource =
                    (TransactionalDataSource) dataSource;
            transactionalDataSource.addPreCommitListener(this);
            transactionalDataSource.addPostCommitListener(this);
        }

        if(state == State.RUNNING)
            dataSource.startup();
    }

    protected void loadModuleSearchPaths() throws BigDBException {
        for (ModuleSearchPathConfig searchPath : config.module_search_paths) {
            File directory = new File(searchPath.path);
            schema.getModuleLocator().addSearchPath(directory,
                    searchPath.recursive);
        }
    }

    protected void loadDataSources() throws BigDBException {
        if (config.data_sources != null) {
            for (DataSourceConfig dataSourceConfig : config.data_sources) {
                String dataSourceName = dataSourceConfig.name;
                Class<?> implementationClass;
                try {
                    implementationClass =
                            Class.forName(dataSourceConfig.implementation_class);
                } catch (ClassNotFoundException exc) {
                    throw new DataSourceException(dataSourceName,
                            "Data source implementation class not found", exc);
                }
                Constructor<?> constructor;
                DataSource dataSource = null;
                try {
                    try {
                        constructor =
                                implementationClass.getConstructor(
                                        String.class, boolean.class, Schema.class, Map.class);
                        dataSource =
                                (DataSource) constructor.newInstance(
                                        dataSourceName,
                                        dataSourceConfig.config,
                                        schema,
                                        dataSourceConfig.properties);
                    } catch (NoSuchMethodException exc) {
                        constructor =
                                implementationClass
                                        .getConstructor(String.class, boolean.class, Schema.class);
                        if (!dataSourceConfig.properties.isEmpty()) {
                            throw new BigDBException(
                                    "Configuration error. "
                                            + "Properties were specified for a data source "
                                            + "that doesn't expect any properties.");
                        }
                        dataSource =
                                (DataSource) constructor
                                        .newInstance(dataSourceName, dataSourceConfig.config, schema);
                    }
                } catch (NoSuchMethodException exc2) {
                    throw new DataSourceException(
                            dataSourceName,
                            "No appropriate constructor in implementation class.",
                            exc2);
                } catch (Exception exc) {
                    throw new DataSourceException(dataSourceName,
                            "Error instantiating data source.", exc);
                }
                assert dataSource != null;
                dataSources.put(dataSource.getName(), dataSource);
            }
        }
    }

    private static class DataSourceMapperVisitor extends
            AbstractSchemaNodeVisitor {

        protected List<DataSourceMapping> dataSourceMappings;

        public DataSourceMapperVisitor(
                List<DataSourceMapping> dataSourceMappings) {
            this.dataSourceMappings = dataSourceMappings;
        }

        public void assignDataSource(SchemaNode schemaNode) {
            String dataSource = null;
            for (DataSourceMapping mapping : dataSourceMappings) {
                if (mapping.matches(schemaNode)) {
                    dataSource = mapping.getDataSource(schemaNode);
                    if ((dataSource != null) && !dataSource.isEmpty())
                        break;
                }
            }
            schemaNode.setDataSource(dataSource);
        }

        @Override
        public SchemaNodeVisitor.Result visit(LeafSchemaNode leafSchemaNode)
                throws BigDBException {
            assignDataSource(leafSchemaNode);
            return SchemaNodeVisitor.Result.CONTINUE;
        }

        @Override
        public SchemaNodeVisitor.Result visit(
                LeafListSchemaNode leafListSchemaNode) throws BigDBException {
            assignDataSource(leafListSchemaNode);
            // It's convenient to have the data source also specified for the
            // leaf schema node contained in the leaf list schema node, so
            // also set that here.
            if (leafListSchemaNode.getDataSources().iterator().hasNext()) {
                String dataSource =
                        leafListSchemaNode.getDataSources().iterator().next();
                LeafSchemaNode leafSchemaNode =
                        leafListSchemaNode.getLeafSchemaNode();
                leafSchemaNode.setDataSource(dataSource);
            }
            return SchemaNodeVisitor.Result.CONTINUE;
        }

        @Override
        public SchemaNodeVisitor.Result visit(
                ReferenceSchemaNode referenceSchemaNode) throws BigDBException {
            assignDataSource(referenceSchemaNode);
            return SchemaNodeVisitor.Result.CONTINUE;
        }

        @Override
        public SchemaNodeVisitor.Result visit(
                TypedefSchemaNode typedefSchemaNode) throws BigDBException {
            assignDataSource(typedefSchemaNode);
            return SchemaNodeVisitor.Result.CONTINUE;
        }

        @Override
        public SchemaNodeVisitor.Result visit(UsesSchemaNode usesSchemaNode)
                throws BigDBException {
            throw new BigDBException("Cannot map data source for uses node: " +
                    usesSchemaNode.getName());
            // should not be here.
        }
    }

    protected void mapDataSources() throws BigDBException {
        // Map the schema nodes to data sources
        // First load the data source mappings from the config.
        List<DataSourceMapping> dataSourceMappings =
                new ArrayList<DataSourceMapping>();
        if (config.data_source_mappings != null) {
            for (DataSourceMappingConfig mappingConfig : config.data_source_mappings) {
                DataSourceMapping mapping =
                        new DataSourceMapping(mappingConfig.predicate,
                                mappingConfig.data_source);
                dataSourceMappings.add(mapping);
            }
        }

        // Now visit all of nodes in the schema tree and apply the mappings
        DataSourceMapperVisitor mappingVisitor =
                new DataSourceMapperVisitor(dataSourceMappings);
        schema.getSchemaRoot().accept(mappingVisitor);
    }

    protected void loadModules() throws BigDBException {
        // Load the schema information from the configured modules
        if (config.modules != null) {
            for (ModuleConfig moduleConfig : config.modules) {
                logger.trace("Loading module config {}", moduleConfig.name);
                String directory = moduleConfig.directory;
                if ((directory != null) && directory.isEmpty())
                    directory = null;
                String name = moduleConfig.name;
                if ((name != null) && name.isEmpty())
                    name = null;
                ModuleIdentifier moduleId = null;
                if (name != null)
                    moduleId =
                            new ModuleIdentifier(name, moduleConfig.revision);

                // We don't do anything with the returned module, so we don't
                // really
                // need to assign the module variable, but it's useful for
                // debugging.
                @SuppressWarnings("unused")
                ModuleImpl module;
                if (directory != null) {
                    File directoryFile = new File(directory);
                    if (name != null) {
                        module =
                                schema.loadModule(moduleId, directoryFile,
                                        false);
                    } else {
                        // TODO: Implement this!
                        throw new UnsupportedOperationException(
                                "Loading all modules in a directory not supported yet");
                    }
                } else {
                    assert name != null;
                    module = schema.loadModule(moduleId);
                }
            }
        }

        // Complete the loading of the schema. This involves making any further
        // passes over the code to handle things that can't be handled at the
        // point where the modules are initially loaded, e.g. resolving
        // forward references to typedefs.
        schema.finishLoading();

        mapDataSources();
    }

    private void initDataSources() throws BigDBException {
        for (DataSource dataSource : dataSources.values()) {
            if(dataSource instanceof TreespaceAware)
                ((TreespaceAware) dataSource).setTreespace(this);

            dataSource.setMutationListener(this);
            // FIXME: Get rid of code duplication with registerDataSource
            if (dataSource instanceof TransactionalDataSource) {
                TransactionalDataSource transactionalDataSource =
                        (TransactionalDataSource) dataSource;
                transactionalDataSource.addPreCommitListener(this);
                transactionalDataSource.addPostCommitListener(this);
            }
        }
    }

    private void configure() throws BigDBException {
        loadModuleSearchPaths();
        loadDataSources();
        loadModules();
        initDataSources();
        initJsonHandler();
    }

    @Override
    public synchronized void startup() throws BigDBException {
        if(state != State.STOPPED)
            throw new IllegalStateException("Treespace already running");

        for (DataSource dataSource : dataSources.values()) {
            dataSource.startup();
        }

        this.state = State.RUNNING;
    }

    private void initJsonHandler() {
        jsonHandler = new DataNodeJsonHandler(dataSources);
    }


    public Iterable<DataNodeWithPath> performQuery(Query query,
            BigDBOperation operation, Iterable<DataSource> dataSources,
            AuthContext authContext, boolean expandTrailingList)
                    throws BigDBException {
        // This should really be private but currently it's called from
        // DynamicDataSource as a short-term workaround until the mutation
        // code can be refactored.
        SchemaNode rootSchemaNode = schema.getSchemaRoot();
        LogicalDataNodeBuilder logicalBuilder =
                new LogicalDataNodeBuilder(schema.getSchemaRoot());
        for (DataSource dataSource : dataSources) {
            Query.StateType stateType =
                    dataSource.isConfig() ? Query.StateType.CONFIG
                            : Query.StateType.OPERATIONAL;
            if (query.getIncludedStateTypes().contains(stateType)) {
                DataNode rootDataNode = dataSource.getRoot(authContext, query);
                logicalBuilder.addContribution(dataSource, rootDataNode);
                if (logger.isDebugEnabled()) {
                    logger.debug("Enabling data source \"{}\" for query \"{}\"",
                            dataSource.getName(), query);
                }
            }
        }
        DataNode logicalDataNode = logicalBuilder.getDataNode();
        if (logicalDataNode.isNull())
            return Collections.<DataNodeWithPath>emptyList();
        FilterHook.Operation filterOperation =
                (operation == BigDBOperation.QUERY)
                        ? FilterHook.Operation.QUERY
                        : FilterHook.Operation.MUTATION;
        DataNode filteredDataNode =
                new FilterDictionaryDataNode(rootSchemaNode,
                        logicalDataNode, LocationPathExpression.ROOT_PATH,
                        hookRegistry, filterOperation, authContext);
        Iterable<DataNodeWithPath> dataNodes =
                filteredDataNode.queryWithPath(rootSchemaNode, query
                        .getBasePath(), expandTrailingList);
        if (!query.getSelectedPaths().isEmpty()) {
            Path simpleBasePath = query.getBasePath().getSimplePath();
            SchemaNode baseSchemaNode =
                    rootSchemaNode.getDescendantSchemaNode(simpleBasePath);
            String baseNodeName = baseSchemaNode.getName();
            Collection<LocationPathExpression> adjustedSelectedPaths =
                    new ArrayList<LocationPathExpression>();
            for (LocationPathExpression selectedPath : query
                    .getSelectedPaths()) {
                LocationPathExpression adjustedSelectedPath =
                        LocationPathExpression
                                .ofPaths(LocationPathExpression
                                        .ofName(baseNodeName), selectedPath);
                adjustedSelectedPaths.add(adjustedSelectedPath);
            }
            dataNodes =
                    new SelectDataNodeIterable(baseSchemaNode,
                            adjustedSelectedPaths, dataNodes);
        }

        Iterable<DataNodeWithPath> authDataNodes = dataNodes;
// FIXME: Disabling this code to try to handle invoking authorization hooks
// even if the result of the query was empty. This didn't work correctly in
// the case where the input query contained wildcarded components in the path
// since the authorization hooks expect that the data nodes are fully qualified.
// Need to revisit this.
//        Iterable<DataNodeWithPath> authDataNodes =
//                dataNodes.iterator().hasNext() ? dataNodes :
//                    ImmutableList.<DataNodeWithPath>of(
//                            new DataNodeWithPathImpl(query.getBasePath(),
//                                    DataNode.DELETED));
        logger.trace("authDataNodes: " + authDataNodes);
        AuthorizationHook.Operation authOperation =
                (operation == BigDBOperation.QUERY)
                        ? AuthorizationHook.Operation.QUERY
                        : AuthorizationHook.Operation.MUTATION;
        for (DataNodeWithPath dataNodeWithPath: authDataNodes) {
            DataNode rootedDataNode = DataNodeUtilities.makeRootedDataNode(
                    rootSchemaNode, null, dataNodeWithPath.getPath(),
                    dataNodeWithPath.getDataNode());
            logger.trace("rootedDataNode: " + rootedDataNode);
            // Call authorization hooks
            AuthorizationHookContextImpl authorizationHookContext =
                    new AuthorizationHookContextImpl(authOperation,
                            DataNode.NULL, rootedDataNode, rootedDataNode,
                            authContext);
            AuthorizationHook.Result authResult =
                    authorize(authorizationHookContext,
                            LocationPathExpression.ROOT_PATH, rootSchemaNode,
                            DataNode.NULL, rootedDataNode, rootedDataNode);
            if (authResult.getDecision() != AuthorizationHook.Decision.ACCEPT)
                throw new AuthorizationException(authResult.toString());
        }

        return dataNodes;
    }

    @Override
    public DataNodeSet queryData(Query query, AuthContext authContext)
            throws BigDBException {

        lock.readLock().lock();

        try {
            Iterable<DataNodeWithPath> result =
                    performQuery(query, BigDBOperation.QUERY,
                            dataSources.values(), authContext, true);
            Iterable<DataNode> dataNodes =
                    new AbstractDataNode.DataNodePathStrippingIterable(result);

            DataNodeSet dataNodeSet = new DataNodeSet(dataNodes);

            if (logger.isTraceEnabled()) {
                logger.trace(String.format(
                        "Performed query: treespace: %s; query: %s; result: %s",
                        getName(), query, dataNodeSet));
            }

            return dataNodeSet;

        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public String
            queryData(Query query, DataFormat format, AuthContext authContext)
                    throws BigDBException {
        String result = "";
        DataNodeSet dataNodeSet = queryData(query, authContext);
        SchemaNode rootSchemaNode = schema.getSchemaRoot();
        boolean matchesMultipleDataNodes =
                DataNodeUtilities.pathMatchesMultipleDataNodes(
                        query.getBasePath(), rootSchemaNode);
        Object resultObject =
                matchesMultipleDataNodes ? dataNodeSet : dataNodeSet
                        .getSingleDataNode();
        if (resultObject != null) {
            try {
                switch (format) {
                case JSON:
                    result = jsonHandler.writeAsString(resultObject);
                    break;
                default:
                    throw new BigDBException("Invalid data format");
                }
            } catch (Exception exc) {
                throw new DataNodeSerializationException(exc);
            }
        }
        return result;
    }

    // FIXME: refactor duplicate definitions from here and MemoryDataSource

    public void mutateData(BigDBOperation operation, Query query,
            DataFormat format, InputStream data, AuthContext authContext)
            throws BigDBException {

        assert operation == BigDBOperation.INSERT ||
                operation == BigDBOperation.REPLACE ||
                operation == BigDBOperation.UPDATE;

        SchemaNode schemaNode = schema.getSchemaNode(query.getBasePath());

        lock.writeLock().lock();
        try {
            Map<String, DataNode> dataSourceMap;
            switch (format) {
            case JSON:
                if ((schemaNode.getNodeType() == SchemaNode.NodeType.LIST) &&
                        (operation == BigDBOperation.UPDATE)) {
                    // For update operations the data is a JSON object, not a
                    // list, representing the fields in each list element to
                    // be updated. So in that case the schema node is the list
                    // element. For insert & replace operations we expect a list
                    ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
                    schemaNode = listSchemaNode.getListElementSchemaNode();
                }
                dataSourceMap =
                        jsonHandler.parseData(query, operation, schemaNode,
                                data);
                break;
            default:
                throw new BigDBException("Invalid input data format");
            }

            for (String dataSourceName: schemaNode.getDataSources()) {
                DataNode dataNode = dataSourceMap.get(dataSourceName);
                if (dataNode == null) {
                    // For insert and update operations if there's no data
                    // mapped to the data source, then there's nothing to do
                    // so we can just continue to the next data source. But
                    // for replace operations we are replacing the list elements
                    // selected in the query with the empty data, so we need
                    // to call replaceData to (effectively) delete the
                    // selected elements.
                    if (operation != BigDBOperation.REPLACE)
                        continue;
                    dataNode = DataNode.NULL;
                }
                DataSource dataSource = dataSources.get(dataSourceName);
                switch (operation) {
                case INSERT:
                    dataSource.insertData(query, dataNode, authContext);
                    break;
                case REPLACE:
                    dataSource.replaceData(query, dataNode, authContext);
                    break;
                case UPDATE:
                    dataSource.updateData(query, dataNode, authContext);
                    break;
                default:
                    assert false;
                    break;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void insertData(Query query, DataFormat format, InputStream data,
            AuthContext authContext) throws BigDBException {
        mutateData(BigDBOperation.INSERT, query, format, data,
                authContext);
    }

    @Override
    public void replaceData(Query query, DataFormat format, InputStream data,
            AuthContext authContext) throws BigDBException {
        mutateData(BigDBOperation.REPLACE, query, format, data,
                authContext);
    }

    @Override
    public void updateData(Query query, DataFormat format, InputStream data,
            AuthContext authContext) throws BigDBException {
        mutateData(BigDBOperation.UPDATE, query, format, data, authContext);
    }

    @Override
    public void deleteData(Query query, AuthContext authContext)
            throws BigDBException {

        lock.writeLock().lock();
        try {
            SchemaNode schemaNode = schema.getSchemaNode(query.getBasePath());
            Set<String> dataSourceNames = schemaNode.getDataSources();
            for (DataSource dataSource : dataSources.values()) {
                if (dataSourceNames.contains(dataSource.getName()))
                    dataSource.deleteData(query, authContext);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void insertData(Query query, DataNode data, AuthContext context)
            throws BigDBException {
        // FIXME: Implement this. Need to implement function to split input
        // data node into the different data source contributions similar to
        // what we do with JSON data.
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceData(Query query, DataNode data, AuthContext context)
            throws BigDBException {
        // FIXME: Implement this. Need to implement function to split input
        // data node into the different data source contributions similar to
        // what we do with JSON data.
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateData(Query query, DataNode data, AuthContext context)
            throws BigDBException {
        // FIXME: Implement this. Need to implement function to split input
        // data node into the different data source contributions similar to
        // what we do with JSON data.
        throw new UnsupportedOperationException();
    }
}
