package net.bigdb.data;

import java.util.Iterator;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;
import net.bigdb.auth.AuthContext;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.FilterHook;
import net.bigdb.hook.HookRegistry;
import net.bigdb.hook.internal.FilterHookContextImpl;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.SchemaNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.AbstractIterator;

/**
 * Implementation of a keyed list data node that invoked any registered filter
 * hooks to possibly filter/exclude the list elements.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class FilterKeyedListDataNode extends AbstractKeyedListDataNode {
    private final static Logger logger =
        LoggerFactory.getLogger(FilterKeyedListDataNode.class);

    private class FilterKeyedListEntryIterator extends
            AbstractIterator<KeyedListEntry> {

        private Iterator<KeyedListEntry> sourceIterator;

        FilterKeyedListEntryIterator() {
            try {
                this.sourceIterator =
                        sourceDataNode.getKeyedListEntries().iterator();
            } catch (BigDBException e) {
                throw new BigDBInternalError(
                        "Error building filter keyed list entry iterator", e);
            }
        }

        @Override
        protected KeyedListEntry computeNext() {
            while (sourceIterator.hasNext()) {
                KeyedListEntry keyedListEntry = sourceIterator.next();
                IndexValue keyValue = keyedListEntry.getKeyValue();

                DataNode listElementDataNode = keyedListEntry.getDataNode();
                LocationPathExpression listElementLocationPath =
                        DataNodeUtilities.getListElementLocationPath(
                                locationPath, keyValue);
                FilterHook.Result filterResult =
                        invokeFilterHooks(listElementLocationPath,
                                listElementDataNode);
                if (logger.isDebugEnabled()) {
                    if (filterResult == FilterHook.Result.EXCLUDE) {
                        logger.debug("List element \"{}\" excluded by filter hook",
                                listElementLocationPath);
                    }
                }
                // If the list element wasn't filtered, then wrap the list
                // element data node with another filter and return that.
                // Otherwise, advance to the next list element.
                if (filterResult == FilterHook.Result.INCLUDE) {
                    DataNode filterListElementDataNode =
                            new FilterDictionaryDataNode(schemaNode
                                    .getListElementSchemaNode(), keyedListEntry
                                    .getDataNode(), listElementLocationPath,
                                    hookRegistry, operation, authContext);
                    return new KeyedListEntryImpl(keyedListEntry.getKeyValue(),
                            filterListElementDataNode);
                }
            }
            return endOfData();
        }
    }

    private class FilterKeyedListEntryIterable implements
            Iterable<KeyedListEntry> {
        @Override
        public Iterator<KeyedListEntry> iterator() {
            return new FilterKeyedListEntryIterator();
        }
    }

    /**
     * The schema node corresponding to this data node. This should be a
     * ListSchemaNode.
     */
    private final ListSchemaNode schemaNode;

    /** The underlying data node that's being filtered. */
    private final DataNode sourceDataNode;

    /** The location path of this data node */
    private final LocationPathExpression locationPath;

    /** The hook registry used to look up the filter hooks */
    private final HookRegistry hookRegistry;

    /** The operation being performed */
    private final FilterHook.Operation operation;

    /** The auth context to be passed to the filter hooks */
    private final AuthContext authContext;

    public FilterKeyedListDataNode(SchemaNode schemaNode,
            DataNode sourceDataNode, LocationPathExpression locationPath,
            HookRegistry hookRegistry, FilterHook.Operation operation,
            AuthContext authContext) {

        super();

        assert schemaNode != null;
        assert schemaNode instanceof ListSchemaNode;
        assert sourceDataNode != null;
        assert locationPath != null;
        assert hookRegistry != null;
        assert operation != null;

        this.schemaNode = (ListSchemaNode) schemaNode;
        this.sourceDataNode = sourceDataNode;
        this.locationPath = locationPath;
        this.hookRegistry = hookRegistry;
        this.operation = operation;
        this.authContext = authContext;
    }

    @Override
    public DigestValue computeDigestValue() {
        // Computing the digest values for filter data nodes would be
        // expensive so for now we just don't support it.
        // We'll see if it's needed.
        return null;
    }

    @Override
    public IndexSpecifier getKeySpecifier() {
        return schemaNode.getKeySpecifier();
    }

    @Override
    public Iterable<KeyedListEntry> getKeyedListEntries() {
        return new FilterKeyedListEntryIterable();
    }

    private FilterHook.Result invokeFilterHooks(
            LocationPathExpression listElementLocationPath,
            DataNode listElementDataNode) {
        // Look up and invoke any registered filter hooks. If any of
        // the hooks excludes the child data node, then we move onto
        // the next element.
        List<FilterHook> filterHooks =
                hookRegistry.getFilterHooks(locationPath);
        if (!filterHooks.isEmpty()) {
            FilterHookContextImpl filterHookContext =
                    new FilterHookContextImpl(operation,
                            listElementLocationPath, listElementDataNode,
                            authContext);
            for (FilterHook filterHook : filterHooks) {
                try {
                    FilterHook.Result result = filterHook.filter(filterHookContext);
                    if (result == FilterHook.Result.EXCLUDE) {
                        return result;
                    }
                }
                catch (BigDBException e) {
                    // This is indicative of an error in the implementation
                    // of the filter hook.
                    throw new BigDBInternalError("Error invoking filter hook", e);
                }
            }
        }
        return FilterHook.Result.INCLUDE;
    }

    @Override
    public DataNode getChild(IndexValue keyValue) throws BigDBException {
        DataNode childDataNode = sourceDataNode.getChild(keyValue);
        if (childDataNode.isNull())
            return DataNode.NULL;

        LocationPathExpression listElementLocationPath =
                DataNodeUtilities.getListElementLocationPath(locationPath,
                        keyValue);
        FilterHook.Result filterResult =
                invokeFilterHooks(listElementLocationPath, childDataNode);
        if (filterResult == FilterHook.Result.EXCLUDE) {
            if (logger.isDebugEnabled()) {
                logger.debug("List element \"{}\" excluded by filter hook",
                        listElementLocationPath);
            }
            return DataNode.NULL;
        }

        DataNode filteredListElementDataNode =
                new FilterDictionaryDataNode(schemaNode
                        .getListElementSchemaNode(), childDataNode,
                        listElementLocationPath, hookRegistry, operation,
                        authContext);
        return filteredListElementDataNode;
    }
}
