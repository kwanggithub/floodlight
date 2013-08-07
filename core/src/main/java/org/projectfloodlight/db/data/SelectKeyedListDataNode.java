package org.projectfloodlight.db.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;

import com.google.common.collect.AbstractIterator;

/**
 * Implementation of a keyed list data node that applies the selected paths that
 * were specified in the "select" params of the query to filter the child nodes
 * that are visible from an underlying data node
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class SelectKeyedListDataNode extends AbstractKeyedListDataNode {

    private class SelectKeyedListEntryIterator extends AbstractIterator<KeyedListEntry> {

        private Iterator<KeyedListEntry> sourceIterator;

        SelectKeyedListEntryIterator() {
            try {
                this.sourceIterator =
                        sourceDataNode.getKeyedListEntries().iterator();
            } catch (BigDBException e) {
                throw new BigDBInternalError(
                        "Error building select keyed list entry iterator", e);
            }
        }

        @Override
        protected KeyedListEntry computeNext() {
            while (sourceIterator.hasNext()) {
                KeyedListEntry keyedListEntry = sourceIterator.next();
                IndexValue keyValue = keyedListEntry.getKeyValue();
                try {
                    DataNode selectedDataNode = getChild(keyValue);
                    if (!selectedDataNode.isNull()) {
                        return new KeyedListEntryImpl(keyValue, selectedDataNode);
                    }
                }
                catch (BigDBException e) {
                    throw new BigDBInternalError("Unexpected error in getting child", e);
                }
            }
            return endOfData();
        }
    }

    private class SelectKeyedListEntryIterable implements Iterable<KeyedListEntry> {
        @Override
        public Iterator<KeyedListEntry> iterator() {
            return new SelectKeyedListEntryIterator();
        }
    }

    /**
     * The schema node corresponding to this data node. This should be a
     * ListSchemaNode.
     */
    private final ListSchemaNode schemaNode;

    /** The underlying data node that's being filtered for the selected paths */
    private final DataNode sourceDataNode;

    /** The selected paths that are visible to callers of this data node */
    private final Collection<LocationPathExpression> selectedPaths =
            new ArrayList<LocationPathExpression>();

    /**
     * If there's a single selected list element, i.e. specified by an exact
     * match predicate, then this is set to the selected key value
     */
    private IndexValue singleSelectedKeyValue;
    private DataNode singleSelectedDataNode;

    public SelectKeyedListDataNode(SchemaNode schemaNode,
            DataNode sourceDataNode,
            Collection<LocationPathExpression> selectedPaths)
            throws BigDBException {

        super();

        assert schemaNode != null;
        assert schemaNode instanceof ListSchemaNode;
        assert sourceDataNode != null;
        assert sourceDataNode.isKeyedList();
        assert selectedPaths != null;

        this.schemaNode = (ListSchemaNode) schemaNode;
        this.sourceDataNode = sourceDataNode;
        this.selectedPaths.addAll(selectedPaths);

        IndexSpecifier keySpecifier = this.schemaNode.getKeySpecifier();
        for (LocationPathExpression selectedPath: selectedPaths) {
            if (selectedPath.size() == 0) {
                singleSelectedKeyValue = null;
                break;
            }
            Step step = selectedPath.getStep(0);
            IndexValue keyValue = DataNodeUtilities.getKeyValue(keySpecifier, step);
            if (keyValue != null) {
                if (singleSelectedKeyValue != null) {
                    singleSelectedKeyValue = null;
                    break;
                }
                singleSelectedKeyValue = keyValue;
            }
        }
        if (singleSelectedKeyValue != null) {
            singleSelectedDataNode = sourceDataNode.getChild(singleSelectedKeyValue);
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
    public IndexSpecifier getKeySpecifier() {
        return schemaNode.getKeySpecifier();
    }


    @Override
    public Iterable<KeyedListEntry> getKeyedListEntries() {
        if (singleSelectedKeyValue != null) {
            KeyedListEntry keyedListEntry =
                    new KeyedListEntryImpl(singleSelectedKeyValue, singleSelectedDataNode);
            return Collections.singleton(keyedListEntry);
        }
        return new SelectKeyedListEntryIterable();
    }

    @Override
    public DataNode getChild(IndexValue keyValue) throws BigDBException {
        if (singleSelectedKeyValue != null) {
            return singleSelectedKeyValue.equals(keyValue)
                    ? singleSelectedDataNode : DataNode.NULL;
        }

        DataNode listElementDataNode = sourceDataNode.getChild(keyValue);
        if (listElementDataNode.isNull())
            return DataNode.NULL;

        SchemaNode listElementSchemaNode =
                schemaNode.getListElementSchemaNode();
        Step listElementStep =
                DataNodeUtilities.getListElementStep(schemaNode.getName(),
                        keyValue);

        Collection<LocationPathExpression> matchingSelectedPaths =
                new ArrayList<LocationPathExpression>();
        for (LocationPathExpression selectedPath : selectedPaths) {
            Step listStep = selectedPath.getStep(0);
            if (matchesPredicates(listElementSchemaNode,
                    listElementDataNode, listElementStep, listStep)) {
                matchingSelectedPaths.add(selectedPath);
            }
        }

        if (matchingSelectedPaths.isEmpty())
            return DataNode.NULL;

        DataNode selectedListElementDataNode =
                new SelectDictionaryDataNode(schemaNode
                        .getListElementSchemaNode(), listElementDataNode,
                        matchingSelectedPaths);

        return selectedListElementDataNode;
    }
}
