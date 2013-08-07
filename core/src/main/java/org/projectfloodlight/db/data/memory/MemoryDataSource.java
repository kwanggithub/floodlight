package org.projectfloodlight.db.data.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.data.AbstractDataSource;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeTypeMismatchException;
import org.projectfloodlight.db.data.DataNodeUtilities;
import org.projectfloodlight.db.data.DiffContainerDataNode;
import org.projectfloodlight.db.data.IndexSpecifier;
import org.projectfloodlight.db.data.IndexValue;
import org.projectfloodlight.db.data.ListDataNode;
import org.projectfloodlight.db.data.MutationListener;
import org.projectfloodlight.db.data.PredicateMatchingListElementIterator;
import org.projectfloodlight.db.data.TransactionalDataSource;
import org.projectfloodlight.db.data.TreespaceAware;
import org.projectfloodlight.db.data.DataNode.DataNodeWithPath;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.ListElementSchemaNode;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.Schema;
import org.projectfloodlight.db.schema.SchemaNode;
import org.projectfloodlight.db.service.BigDBOperation;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.db.service.internal.TreespaceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterators;

/**
 * Data source implementation that stores data in memory. All changes are lost
 * when BigDB is restarted.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class MemoryDataSource extends AbstractDataSource
    implements DelegatableDataSource, TransactionalDataSource, TreespaceAware {

    private final static Logger logger = LoggerFactory.getLogger(MemoryDataSource.class);

    protected volatile DataNode root;
    protected List<PreCommitListener> preCommitListeners =
            new CopyOnWriteArrayList<PreCommitListener>();
    protected List<PostCommitListener> postCommitListeners =
            new CopyOnWriteArrayList<PostCommitListener>();

    private TreespaceImpl treespace;

    public MemoryDataSource(String name, boolean config, Schema schema) throws BigDBException {
        super(name, config, schema, new MemoryDataNodeFactory());
        root = new MemoryContainerDataNode(false, null);
    }

    @Override
    public void addPreCommitListener(PreCommitListener listener) {
        preCommitListeners.add(listener);
    }

    @Override
    public void addPostCommitListener(PostCommitListener listener) {
        postCommitListeners.add(listener);
    }

    @Override
    public Iterable<DataNodeWithPath> queryData(Query query,
            AuthContext authContext) throws BigDBException {
        assert query != null;

        Iterable<DataNodeWithPath> result =
                root.queryWithPath(getRootSchemaNode(), query.getBasePath());
        return result;
    }

    /**
     * As we invoke mutateData recursively we build up both the new mutated
     * tree and a tree that represents all of the nodes that were
     * touched/written by the operation even if the newly written value is the
     * same as the old value. We need to track the written nodes to make sure
     * that we properly authorize all of the data nodes that would be written
     * by the mutation operation. This class represents the combined result of
     * these two data node trees that we build during mutateData.
     *
     * @author rob.vaterlaus@bigswitch.com
     */
    private static class MutateDataResult {

        final DataNode newDataNode;
        final DataNode writtenDataNode;

        MutateDataResult(DataNode newDataNode, DataNode writtenDataNode) {
            this.newDataNode = newDataNode;
            this.writtenDataNode = writtenDataNode;
        }
    }

    /**
     * Apply a mutation operation to the child of a dictionary (i.e. either a
     * container or list element) data node. When we're still recursively
     * evaluating the steps in the path specifying the nodes to be mutated this
     * is called with a child name obtained from the step in the path. For
     * update/patch operations the recursive mutateData calls continue into the
     * update data. In that case this is called for each of the children
     * specified in the update data.
     *
     * @param operation
     *            Which mutation operation is being performed
     * @param path
     *            The location path corresponding to the dictionary node being
     *            mutated
     * @param schemaNode
     *            Schema node corresponding to the dictionary node
     * @param dataNode
     *            The dictionary data node being mutated
     * @param childName
     *            The name of the child data node to mutate
     * @param mutatePath
     *            The path representing the remaining portion of the overall
     *            mutation path at the current recursion level. If this is being
     *            invoked as we're still descending through the overall mutation
     *            path then the first step in this path is the step
     *            corresponding to the specified child name, i.e. the name of
     *            that step equals childName. If this is called as we're
     *            recursively processing the mutation data, then mutatePath is
     *            empty.
     * @param mutateDataNode
     *            The data node representing the data to mutate. If we're still
     *            processing the mutatePath, then this node corresponds to the
     *            data specified in the overall mutation operation. As we
     *            descend into the overall mutation data, then this node is a
     *            descendant data node of the overall mutation data
     * @param writtenChildNodes
     *            This map represents the child nodes that were touched/written
     *            by the mutation operation. This map is updated with the data
     *            node that was written corresponding to the specified child
     *            node. This map is updated even if the new value is the same as
     *            the old value.
     * @param updatedChildNodes
     *            This map represents the modified child nodes of the dictionary
     *            data node that are used to construct the new tree
     * @param deletedChildNodes
     *            This set represents the child data nodes that were deleted
     *            from the dictionary data node
     * @param mutatedNodes
     *            This map contains all of the data node paths that were
     *            affected by the mutation operation. This is used to perform
     *            the MutationListener notifications to application code. NOTE:
     *            MutationListener is deprecated and superseded by the WatchHook
     *            mechanism. Hopefully we can update the code to use watch hooks
     *            soon and get rid of this
     * @throws BigDBException
     */
    private void mutateDictionaryChild(BigDBOperation operation,
            LocationPathExpression path, SchemaNode schemaNode,
            DataNode dataNode, String childName,
            LocationPathExpression mutatePath, DataNode mutateDataNode,
            Map<String, DataNode> writtenChildNodes,
            Map<String, DataNode> updatedChildNodes, Set<String> deletedChildNodes,
            Map<LocationPathExpression, MutationListener.Operation> mutatedNodes)
                    throws BigDBException {
        // Get all of the child info
        LocationPathExpression childPath = path.getChildLocationPath(childName);
        SchemaNode childSchemaNode = schemaNode.getChildSchemaNode(childName);
        assert childSchemaNode != null;
        DataNode childDataNode = dataNode.getChild(childName);
        LocationPathExpression childMutatePath;
        DataNode childMutateDataNode;
        if (mutatePath.size() > 0) {
            if (childSchemaNode.getNodeType() == SchemaNode.NodeType.LIST) {
                // For lists the first step in the mutate path is the one
                // associated with the list itself so that it can evaluate
                // the predicates to determine which list elements match.
                childMutatePath = mutatePath;
            } else {
                // Predicates are only allowed/supported for list nodes
                if (!mutatePath.getStep(0).getPredicates().isEmpty()) {
                    throw new BigDBException(
                            "Path predicates are only allowed with list nodes");
                }
                // For node types other than lists the first step is the one
                // associated with the next child to resolve, i.e. not the
                // one associated with childMutateDataNode.
                childMutatePath = mutatePath.subpath(1);
            }
            childMutateDataNode = mutateDataNode;
        } else {
            childMutatePath = mutatePath;
            childMutateDataNode = mutateDataNode.getChild(childName);
        }

        // Check to make sure that an update operation isn't trying to modify
        // one of the key fields of a list element.
        if ((schemaNode.getNodeType() == SchemaNode.NodeType.LIST_ELEMENT) &&
                (operation == BigDBOperation.UPDATE)) {
            ListElementSchemaNode listElementSchemaNode =
                    (ListElementSchemaNode) schemaNode;
            List<String> keyNames = listElementSchemaNode.getKeyNodeNames();
            if (keyNames.contains(childName)) {
                throw new BigDBException(String.format(
                        "Key field \"%s\" of list element cannot be modified",
                        childName));
            }
        }

        // Call mutateData recursively to get the mutated child node
        MutateDataResult childResult = mutateData(operation, childPath,
                childSchemaNode, childDataNode, childMutatePath,
                childMutateDataNode, mutatedNodes);

        // Update the maps/sets used by mutateData to build the new data node
        // tree and the written data node tree
        writtenChildNodes.put(childName, childResult.writtenDataNode);
        if (childResult.newDataNode.isNull()) {
            deletedChildNodes.add(childName);
        } else {
            updatedChildNodes.put(childName, childResult.newDataNode);
        }
    }

    private MutateDataResult mutateData(BigDBOperation operation,
            LocationPathExpression path, SchemaNode schemaNode,
            DataNode dataNode, LocationPathExpression mutatePath,
            DataNode mutateDataNode,
            Map<LocationPathExpression, MutationListener.Operation> mutatedNodes)
                    throws BigDBException {

        assert operation != null;
        assert path != null;
        assert schemaNode != null;
        assert dataNode != null;
        assert mutatePath != null;
        assert mutateDataNode != null;

        SchemaNode.NodeType nodeType = schemaNode.getNodeType();

        Step step = null;
        String stepName = null;
        boolean hasPredicates = false;
        if (mutatePath.size() > 0) {
            step = mutatePath.getStep(0);
            stepName = step.getName();
            hasPredicates = !step.getPredicates().isEmpty();
        }

        DataNode newDataNode = dataNode;
        DataNode writtenDataNode;

        switch (nodeType) {
        case CONTAINER:
        case LIST_ELEMENT:
            Map<String, DataNode> updatedChildNodes = new HashMap<String, DataNode>();
            Set<String> deletedChildNodes = new HashSet<String>();
            Map<String, DataNode> writtenChildNodes = new HashMap<String, DataNode>();
            if (step != null) {
                // We're still descending through the location path specifying
                // the node(s) to mutate, so we just call mutateDictionaryChild
                // with the child name obtained from the path
                mutateDictionaryChild(operation, path, schemaNode, dataNode,
                        stepName, mutatePath, mutateDataNode,
                        writtenChildNodes, updatedChildNodes,
                        deletedChildNodes, mutatedNodes);
                writtenDataNode = new MemoryContainerDataNode(false,
                        writtenChildNodes);
            } else {
                // We're at the end of the mutatePath and recursively processing
                // the mutation data. In this case we're iterate over all of the
                // children specified in the mutate data.
                for (String childName : mutateDataNode.getChildNames()) {
                    mutateDictionaryChild(operation, path, schemaNode,
                            dataNode, childName, mutatePath, mutateDataNode,
                            writtenChildNodes, updatedChildNodes,
                            deletedChildNodes, null);
                }
                writtenDataNode = new MemoryContainerDataNode(false,
                        writtenChildNodes);
                if (mutatedNodes != null) {
                    MutationListener.Operation mutationOperation =
                            (operation == BigDBOperation.DELETE)
                                    ? MutationListener.Operation.DELETE
                                    : MutationListener.Operation.MODIFY;
                    mutatedNodes.put(path, mutationOperation);
                }
                if (operation == BigDBOperation.DELETE) {
                    newDataNode = DataNode.NULL;
                    break;
                }
            }
            if (!dataNode.isNull()) {
                // Updating existing data node
                if (nodeType == SchemaNode.NodeType.CONTAINER) {
                    newDataNode = new MemoryContainerDataNode(
                            (MemoryContainerDataNode)dataNode,
                            false, updatedChildNodes, deletedChildNodes);
                } else {
                    newDataNode = new MemoryListElementDataNode(
                            (MemoryListElementDataNode)dataNode,
                            false, updatedChildNodes, deletedChildNodes);
                }
            } else if (!updatedChildNodes.isEmpty()) {
                // Creating new data node
                if (nodeType == SchemaNode.NodeType.CONTAINER) {
                    newDataNode = new MemoryContainerDataNode(false,
                            updatedChildNodes);
                } else {
                    newDataNode = new MemoryListElementDataNode(false,
                            updatedChildNodes);
                }
            }
            break;
        case LIST:
            boolean isLastStep = mutatePath.size() <= 1;
            ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
            IndexSpecifier keySpecifier = listSchemaNode.getKeySpecifier();
            if ((keySpecifier == null) && (hasPredicates || !isLastStep)) {
                    throw new BigDBException(
                            "Unsupported mutation operation on an unkeyed list");
            }
            ListElementSchemaNode listElementSchemaNode =
                    listSchemaNode.getListElementSchemaNode();

            // Get an iterator over the list elements selected by the resource
            // path in the query, i.e. reflecting the predicate(s), if any,
            // for the list node.
            Iterator<DataNode> listElementIterator =
                    Iterators.<DataNode>emptyIterator();
            if (hasPredicates) {
                IndexValue keyValue = DataNodeUtilities.getKeyValue(
                        listSchemaNode, step);
                if (keyValue != null) {
                    // Single element is selected, so just create a singleton
                    // iterator for it.
                    DataNode listElementDataNode = dataNode.getChild(keyValue);
                    if (!listElementDataNode.isNull()) {
                        listElementIterator =
                                Iterators.<DataNode>singletonIterator(
                                        listElementDataNode);
                    }
                } else {
                    // More complex predicate(s) are specified. Create the
                    // iterator object that evaluates the predicates for each
                    // element to see if it matches.
                    listElementIterator =
                            new PredicateMatchingListElementIterator(
                                    listElementSchemaNode,
                                    dataNode.iterator(), step);
                }
            } else {
                // No predicates. In most cases this means that we're processing
                // the entire list (e.g. a list replace/delete operation). The
                // exceptions are if it's an insert operation (in which case we
                // don't expect a predicate anyway and are just going to add
                // new elements to the list) or if it's an unkeyed list (in
                // which case there's no key to match on so we wouldn't process
                // the predicates anyway). In those cases we just leave the
                // iterator as an empty iterator, so we skip the loop below.
                if ((operation != BigDBOperation.INSERT) && (keySpecifier != null)) {
                    listElementIterator = dataNode.iterator();
                }
            }

            Map<IndexValue, DataNode> writtenListElements =
                    new HashMap<IndexValue, DataNode>();
            List<DataNode> updatedListElements = new ArrayList<DataNode>();
            Set<IndexValue> deletedListElements = new HashSet<IndexValue>();

            // Iterate over the list elements selected in the query path. If
            // we're not at the end of the path yet, then call mutateData
            // recursively to get the new list element node. If it's the list
            // step, then call applyMutationOperation to get the updated
            // list element node.
            while (listElementIterator.hasNext()) {
                DataNode listElementDataNode = listElementIterator.next();
                IndexValue keyValue = IndexValue.fromListElement(
                        keySpecifier, listElementDataNode);
                if (keyValue == null) {
                    throw new BigDBInternalError(
                            "Missing key value(s) for existing list element");
                }
                LocationPathExpression listElementPath =
                        DataNodeUtilities.getListElementLocationPath(path, keyValue);
                LocationPathExpression childMutatePath = mutatePath.subpath(1);
                BigDBOperation listElementOperation;
                DataNode listElementMutateDataNode;;
                // For replace operation when we're at the end of the
                // mutation path we're deleting the nodes that are being
                // replaced. So in that case we invoke the recursive call with
                // a delete operation.
                if (isLastStep && (operation == BigDBOperation.REPLACE)) {
                    listElementOperation = BigDBOperation.DELETE;
                    listElementMutateDataNode = DataNode.NULL;
                } else {
                    listElementOperation = operation;
                    listElementMutateDataNode = mutateDataNode;
                }
                MutateDataResult listElementResult = mutateData(listElementOperation,
                        listElementPath, listElementSchemaNode,
                        listElementDataNode, childMutatePath,
                        listElementMutateDataNode, mutatedNodes);
                writtenListElements.put(keyValue, listElementResult.writtenDataNode);
                // Make sure we got back a valid list element
                assert listElementResult.newDataNode.isNull() ||
                        (listElementResult.newDataNode.getNodeType() ==
                            DataNode.NodeType.LIST_ELEMENT);
                if (operation != BigDBOperation.UPDATE)
                    deletedListElements.add(keyValue);
                if (!listElementResult.newDataNode.isNull()) {
                    // Make sure the new list element is valid
                    keyValue = IndexValue.fromListElement(keySpecifier,
                            listElementResult.newDataNode);
                    if (keyValue == null) {
                        throw new BigDBInternalError(
                                "Missing key value(s) for new list element");
                    }
                    updatedListElements.add(listElementResult.newDataNode);
                }
            }

            // If we're at the last step process the inserted/replacement nodes
            // and add them to the modified element list.
            if (isLastStep && ((operation == BigDBOperation.INSERT) ||
                    (operation == BigDBOperation.REPLACE))) {
                for (DataNode childMutateDataNode: mutateDataNode) {
                    if (keySpecifier != null) {
                        IndexValue keyValue = IndexValue.fromListElement(
                                keySpecifier, childMutateDataNode);
                        if (keyValue == null) {
                            throw new BigDBException(
                                    "Missing key fields in inserted element");
                        }
                        // Check if we're trying to insert a duplicate list element.
                        // If so, treat that as an error.
                        if (operation == BigDBOperation.INSERT) {
                            if (!dataNode.isNull() && dataNode.hasChild(keyValue)) {
                                throw new BigDBException("List element already exists",
                                        BigDBException.Type.CONFLICT);
                            }
                        }
                        // Add the path for the list element to the mutatedNodes list.
                        LocationPathExpression listElementPath =
                                DataNodeUtilities.getListElementLocationPath(path, keyValue);
                        MutateDataResult listElementResult = mutateData(operation,
                                listElementPath, listElementSchemaNode,
                                DataNode.NULL, LocationPathExpression.EMPTY_PATH,
                                childMutateDataNode, mutatedNodes);
                        writtenListElements.put(keyValue, listElementResult.writtenDataNode);
                        updatedListElements.add(listElementResult.newDataNode);
                    } else {
                        updatedListElements.add(childMutateDataNode);
                    }
                }
                // For unkeyed lists just add a path for the entire unkeyed
                // list to the mutatedNodes, since we can't identify each
                // individual element by a key value.
                if ((keySpecifier == null) && (mutatedNodes != null)) {
                    mutatedNodes.put(path, MutationListener.Operation.MODIFY);
                }
            }
            if (keySpecifier != null) {
                writtenDataNode = new MemoryKeyedListDataNode(keySpecifier,
                        writtenListElements);
            } else {
                writtenDataNode = new MemoryUnkeyedListDataNode(false,
                        writtenListElements.values().iterator());
            }
            // Create the updated (or new) list data node. We don't support
            // updates on unkeyed lists, so for that case we always create
            // a new list.
            if (dataNode.isNull() || !dataNode.isKeyedList()) {
                newDataNode = MemoryDataSource.constructListDataNode(
                        false, keySpecifier, updatedListElements.iterator());
            } else {
                newDataNode = MemoryDataSource.constructListDataNode(
                        dataNode, updatedListElements, deletedListElements);
            }
            if (!newDataNode.hasChildren())
                newDataNode = DataNode.NULL;
            break;
        case LEAF_LIST:
        case LEAF:
            assert step == null;
            MutationListener.Operation mutationOperation;
            if (operation == BigDBOperation.DELETE) {
                newDataNode = DataNode.NULL;
                mutationOperation = MutationListener.Operation.DELETE;
            } else {
                newDataNode = mutateDataNode;
                mutationOperation = MutationListener.Operation.MODIFY;
            }
            writtenDataNode = newDataNode;
            if (mutatedNodes != null)
                mutatedNodes.put(path, mutationOperation);
            break;
        default:
            throw new DataNodeTypeMismatchException("Unexpected data node type");
        }

        return new MutateDataResult(newDataNode, writtenDataNode);
    }

    protected synchronized void mutateData(BigDBOperation operation,
            Query query, DataNode mutateDataNode,
            AuthContext authContext) throws BigDBException {
        SchemaNode rootSchemaNode = getRootSchemaNode();
        LocationPathExpression basePath = query.getBasePath();
        // We a map to keep track of the mutations so that the most recent
        // delete or modify operation on a given location path is the one that's
        // reported to the mutation listeners (and not both).
        Map<LocationPathExpression, MutationListener.Operation> mutatedNodes =
                new HashMap<LocationPathExpression, MutationListener.Operation>();
        MutateDataResult mutateResult =
                mutateData(operation, LocationPathExpression.ROOT_PATH,
                        rootSchemaNode, root, basePath, mutateDataNode,
                        mutatedNodes);

        treespace.authorize(root, mutateResult.newDataNode,
                mutateResult.writtenDataNode, authContext);

        // Update the root
        setRoot(mutateResult.newDataNode);

        // FIXME: Convert this code to use a post-commit listener instead
        if (mutatedNodes.size() > 0) {
            Set<Query> deletedNodes = new HashSet<Query>();
            Set<Query> modifiedNodes = new HashSet<Query>();
            // FIXME: Hack to convert to the mutation listener interface.
            // Really should change the mutation listener mechanism to use
            // location paths instead of queries.
            for (Map.Entry<LocationPathExpression, MutationListener.Operation> entry: mutatedNodes.entrySet()) {
                Query mutatedQuery = Query.of(entry.getKey());
                if (entry.getValue() == MutationListener.Operation.DELETE)
                    deletedNodes.add(mutatedQuery);
                else
                    modifiedNodes.add(mutatedQuery);
            }
            if (!deletedNodes.isEmpty()) {
                mutationListener.dataNodesMutated(deletedNodes,
                        MutationListener.Operation.DELETE, authContext);
            }
            if (!modifiedNodes.isEmpty()) {
                mutationListener.dataNodesMutated(modifiedNodes,
                        MutationListener.Operation.MODIFY, authContext);
            }
        }
    }

    @Override
    public void
            insertData(Query query, DataNode data, AuthContext authContext)
                    throws BigDBException {
        mutateData(BigDBOperation.INSERT, query, data, authContext);
    }

    @Override
    public void
            replaceData(Query query, DataNode data, AuthContext authContext)
                    throws BigDBException {
        mutateData(BigDBOperation.REPLACE, query, data, authContext);
    }

    @Override
    public void updateData(Query query, DataNode data, AuthContext authContext)
            throws BigDBException {
        mutateData(BigDBOperation.UPDATE, query, data, authContext);
    }

    @Override
    public void deleteData(Query query, AuthContext authContext)
            throws BigDBException {
        mutateData(BigDBOperation.DELETE, query, DataNode.NULL, authContext);
    }

    public static ListDataNode constructListDataNode(DataNode baseDataNode,
            List<DataNode> updates, Set<IndexValue> deletions) throws BigDBException {
        if (baseDataNode.isKeyedList()) {
            MemoryKeyedListDataNode keyedBaseDataNode = (MemoryKeyedListDataNode) baseDataNode;
            return new MemoryKeyedListDataNode(keyedBaseDataNode, updates, deletions);
        } else {
            MemoryUnkeyedListDataNode unkeyedBaseDataNode = (MemoryUnkeyedListDataNode) baseDataNode;
            return new MemoryUnkeyedListDataNode(unkeyedBaseDataNode, updates);
        }
    }

    public static ListDataNode constructListDataNode(boolean mutable,
            IndexSpecifier keySpecifier, Iterator<DataNode> initNodes)
                    throws BigDBException {
        return (keySpecifier != null) ?
                new MemoryKeyedListDataNode(mutable, keySpecifier, initNodes) :
                new MemoryUnkeyedListDataNode(mutable, initNodes);
    }

    public static ListDataNode constructListDataNode(IndexSpecifier keySpecifier)
                    throws BigDBException {
        return (keySpecifier != null) ?
                new MemoryKeyedListDataNode(keySpecifier) :
                new MemoryUnkeyedListDataNode();
    }

    @Override
    public DataNode getRoot() throws BigDBException {
        return root;
    }

    @Override
    public void setRoot(DataNode newRoot) throws BigDBException {

        SchemaNode rootSchemaNode = getRootSchemaNode();
        DiffContainerDataNode rootDiffs =
                new DiffContainerDataNode(rootSchemaNode, this.root, newRoot);
        if (!rootDiffs.hasChildren())
            return;

        // Call the pre-commit listeners.
        try {
            for (PreCommitListener preCommitListener: preCommitListeners) {
                preCommitListener.preCommit(this.root, newRoot, rootDiffs);
            }
        }
        catch (BigDBException e) {
            logger.info("Commit rejected by pre-commit listener: " + e.toString(), e);
            throw e;
        }

        this.root = newRoot;

        // Call the post-commit listeners.
        try {
            for (PostCommitListener postCommitListener: postCommitListeners) {
                postCommitListener.postCommit(this.root, newRoot, rootDiffs);
            }
        }
        catch (BigDBException e) {
            // FIXME: What should we do if a post-commit listener throws an exception?
            // We've already committed the new root, so it seems like we should
            // continue and call the rest of the post-commit listeners.
            // But at the end if there was an exception should this raise an
            // exception to the caller?
            logger.error("Exception thrown by post-commit listener: " + e.toString(), e);
        }
    }

    /** This is a temporary hack while DynamicDataSource needs access to treespace */
    @Override
    public synchronized void setTreespace(Treespace treespace) {
        if(getState() != State.CREATED)
            throw new IllegalStateException("Illegal state for treespace configuration: DataSource is not CREATED");

        if(!(treespace instanceof TreespaceImpl))
            throw new IllegalArgumentException("Require instance of TreespaceImpl");

        // FIXME: Get rid of the cast to TreespaceImpl. Should refactor the
        // code so that performQuery isn't a TreespaceImpl method.

        this.treespace = (TreespaceImpl) treespace;
    }


}
