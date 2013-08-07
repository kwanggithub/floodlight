package org.projectfloodlight.db.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;

import com.google.common.collect.UnmodifiableIterator;

/**
 * Implementation of a keyed list data node that invokes dynamic data hooks to
 * populate the contents of the node. The dynamic data hook returns an
 * application-defined object (i.e. not a data node) that would typically be
 * an instance of a class that supports either the Iterable or Iterator
 * interface. That object is mapped to a data node using the data node mapper.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class DynamicKeyedListDataNode extends AbstractKeyedListDataNode {

    /**
     * Implementation of the keyed list entry iterator that wraps the
     * results returned from underlying keyed list entry iterator in a
     * dynamic data node, so that nested dynamic data is merged in.
     */
    private class DynamicKeyedListEntryIterator extends
            UnmodifiableIterator<KeyedListEntry> {

        private Iterator<KeyedListEntry> iterator;

        DynamicKeyedListEntryIterator() {
            try {
                this.iterator = dataNode.getKeyedListEntries().iterator();
            } catch (BigDBException e) {
                throw new BigDBInternalError(
                        "Unexpected error getting keyed list entries", e);
            }
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public KeyedListEntry next() {
            KeyedListEntry entry = iterator.next();
            IndexValue keyValue = entry.getKeyValue();
            DataNode dataNode = entry.getDataNode();
            LocationPathExpression listElementPath =
                    DataNodeUtilities.getListElementLocationPath(locationPath,
                            keyValue);
            try {
                DataNode dynamicDataNode =
                        new DynamicDictionaryDataNode(schemaNode
                                .getListElementSchemaNode(), dataSource,
                                operation, dynamicNode, listElementPath,
                                query, Collections.singleton(dataNode),
                                authContext, requestProperties);
                return new KeyedListEntryImpl(keyValue, dynamicDataNode);
            } catch (BigDBException e) {
                throw new BigDBInternalError(
                        "Unexpected error creating dynamic keyed list entry", e);
            }
        }
    }

    private class DynamicKeyedListEntryIterable implements Iterable<KeyedListEntry> {
        @Override
        public Iterator<KeyedListEntry> iterator() {
            return new DynamicKeyedListEntryIterator();
        }
    }

    // FIXME: Currently there's a fair bit of code duplication between this
    // class and DynamicDictionaryDataNode. Should refactor to reduce the
    // duplication.

    /** The schema node corresponding to this data node */
    private ListSchemaNode schemaNode;

    /** The data source that this data node belongs to */
    private DynamicDataSource dataSource;

    /**
     * The operation type that this dynamic data node is servicing. Currently
     * we create new dynamic data nodes for each individual request.
     * FIXME: Do we really need to make the data node operation-specific or
     * can we infer it from context? Otherwise we need separate root data.
     */
    private DynamicDataHook.Operation operation;

    /**
     * Dynamic node representing the registered dynamic data hooks (and
     * descendant dynamic data hooks) corresponding to this data node.
     */
    private DynamicNode dynamicNode;

    /** The location path of this data node */
    private LocationPathExpression locationPath;

    /**
     * The top-level query specified in the request that initiated the call
     * into the dynamic data source and corresponding dynamic data nodes.
     */
    private Query query;

    /**
     * The data node corresponding to the contributions from all of the
     * registered dynamic data hooks as well as any contributions inherited
     * from the parent data node.
     */
    private DataNode dataNode;

    /** The auth context corresponding to the current request */
    private AuthContext authContext;

    /**
     * The request properties corresponding to the current request. These
     * are used to share state across multiple invocations of different
     * dynamic data hooks across the duration of a single request
     */
    private Map<String, Object> requestProperties;

    public DynamicKeyedListDataNode(SchemaNode schemaNode,
            DynamicDataSource dataSource, DynamicDataHook.Operation operation,
            DynamicNode dynamicNode, LocationPathExpression locationPath,
            Query query, Set<DataNode> parentContributions,
            AuthContext authContext, Map<String, Object> requestProperties)
            throws BigDBException {

        super();

        assert schemaNode != null;
        assert schemaNode instanceof ListSchemaNode;
        assert dataSource != null;
        assert operation != null;
        assert dynamicNode != null;
        assert locationPath != null;
        assert parentContributions != null;
        assert requestProperties != null;

        this.schemaNode = (ListSchemaNode) schemaNode;
        this.dataSource = dataSource;
        this.operation = operation;
        this.dynamicNode = dynamicNode;
        this.locationPath = locationPath;
        this.query = query;
        this.authContext = authContext;
        this.requestProperties = requestProperties;

        Set<DataNode> dataNodes = new HashSet<DataNode>();
        dataNodes.addAll(parentContributions);

        Iterable<DynamicDataHook> dynamicDataHooks =
                dynamicNode.getDynamicDataHooks(operation);
        DynamicDataHookContextImpl context =
                new DynamicDataHookContextImpl(operation, locationPath,
                        query, null, authContext, requestProperties);
        for (DynamicDataHook hook : dynamicDataHooks) {
            Object object = hook.doDynamicData(context);
            if (object instanceof Map<?,?>) {
                object = ((Map<?,?>)object).values();
            }
            if (object != null) {
                DataNode contribution =
                        DataNodeMapper.getDefaultMapper()
                                .convertObjectToDataNode(object, schemaNode);
                if (!contribution.isNull())
                    dataNodes.add(contribution);
            }
        }

        switch (dataNodes.size()) {
        case 0:
            dataNode = DataNode.NULL;
            break;
        case 1:
            dataNode = dataNodes.iterator().next();
            assert dataNode.isKeyedList();
            break;
        default:
            dataNode = CompoundKeyedListDataNode.from(schemaNode, dataNodes);
            break;
        }
    }

    @Override
    public DigestValue computeDigestValue() {
        // Computing the digest values for filter data nodes would be
        // expensive so for now we just don't support it.
        // We'll see if it's needed.
        return null;
    }

    @Override
    public Iterable<KeyedListEntry> getKeyedListEntries()
            throws BigDBException {
        // FIXME: I think this can be optimized if this nodes dynamicNode
        // doesn't have any child dynamic nodes to just return
        // dataNode.getKeyedListEntries instead of the
        // DynamicKeyedListEntryIterable.
        return dynamicNode.getChildDynamicNodes().isEmpty() ?
                dataNode.getKeyedListEntries() :
                    new DynamicKeyedListEntryIterable();
    }

    @Override
    public IndexSpecifier getKeySpecifier() {
        return schemaNode.getKeySpecifier();
    }

    @Override
    public DataNode getChild(IndexValue keyValue) throws BigDBException {

        // Look up the list element corresponding to the specified key
        DataNode listElement = dataNode.getChild(keyValue);

        // If the list element doesn't exist, then there would be nothing to
        // key off of to handle child dynamic data, so we should just return
        // DataNode.NULL in that case. Also, if there are no child dynamic
        // nodes, then there's no point in wrapping the list element in
        // a child dynamic dictionary data node, so we can just return the
        // data node directly.
        if (listElement.isNull() || dynamicNode.getChildDynamicNodes().isEmpty())
            return listElement;

        // Construct and return a dynamic data node corresponding to the
        // list element.
        Set<DataNode> dataNodes = Collections.singleton(listElement);
        SchemaNode listElementSchemaNode =
                schemaNode.getListElementSchemaNode();
        LocationPathExpression listElementPath =
                DataNodeUtilities.getListElementLocationPath(locationPath,
                        keyValue);
        return new DynamicDictionaryDataNode(listElementSchemaNode, dataSource,
                operation, dynamicNode, listElementPath, query, dataNodes,
                authContext, requestProperties);
    }
}
