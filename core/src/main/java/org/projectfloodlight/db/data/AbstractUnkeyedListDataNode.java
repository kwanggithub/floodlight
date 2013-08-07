package org.projectfloodlight.db.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeVisitor.Result;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.ListSchemaNode;
import org.projectfloodlight.db.schema.SchemaNode;

public class AbstractUnkeyedListDataNode extends AbstractListDataNode {

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public DigestValue computeDigestValue() throws BigDBException {
        DigestValue.Builder builder = new DigestValue.Builder();
        builder.update(getNodeType().name());
        builder.update("|UNKEYED|");
        for (DataNode listElementDataNode: this) {
            builder.update(listElementDataNode.getDigestValue().toString());
            builder.update("|");
        }
        return builder.getDigestValue();
    }

    @Override
    protected Iterable<DataNodeWithPath> queryWithPath(SchemaNode schemaNode,
            LocationPathExpression queryPath, boolean expandTrailingList,
            boolean includeEmptyContainers) throws BigDBException {

        // The path must always contain the step for this node, so it's an
        // error if it's called with an empty path.
        if (queryPath.size() == 0) {
            throw new BigDBException("Query path argument cannot be empty");
        }

        ListSchemaNode listSchemaNode = (ListSchemaNode) schemaNode;
        SchemaNode listElementSchemaNode =
                listSchemaNode.getListElementSchemaNode();
        LocationPathExpression remainingQueryPath = queryPath.subpath(1);

        Step listStep = queryPath.getStep(0);
        String listName = listStep.getName();

        List<DataNodeWithPath> result = new ArrayList<DataNodeWithPath>();

        if (expandTrailingList || (queryPath.size() > 1)) {
            // Currently we only support querying the entire unkeyed list or else
            // a single index list element, so check to see if there's a
            // non-negative integer index predicate.
            int index = listStep.getIndexPredicate();
            Iterable<DataNode> listElementDataNodes;
            boolean singleListElement = (index >= 0);
            if (singleListElement) {
                DataNode elementDataNode = getChild(index);
                listElementDataNodes = Collections.singletonList(elementDataNode);
            } else {
                listElementDataNodes = this;
                index = 0;
            }

            // Iterate over the range of list elements set above
            for (DataNode listElementDataNode: listElementDataNodes) {
                Step listElementStep =
                        DataNodeUtilities.getListElementStep(listName, index++);
                if (singleListElement ||
                        matchesPredicates(listElementSchemaNode,
                                listElementDataNode, listElementStep, listStep)) {
                    LocationPathExpression listElementPath =
                            LocationPathExpression.ofStep(listElementStep);
                    if (queryPath.size() > 1) {
                        // There are more steps in the query path, so we need to
                        // call
                        // query recursively
                        LocationPathExpression listElementQueryPath =
                                LocationPathExpression.ofPaths(listElementPath,
                                        remainingQueryPath);
                        Iterable<DataNodeWithPath> listElementResult =
                                listElementDataNode.queryWithPath(
                                        listElementSchemaNode,
                                        listElementQueryPath, expandTrailingList);
                        // Add the results to the results for the overall list
                        for (DataNodeWithPath dataNodeWithPath : listElementResult) {
                            result.add(dataNodeWithPath);
                        }
                    } else {
                        // No more query path to evaluate, so just make a
                        // DataNodeWithPath
                        // for the list element data node.
                        DataNodeWithPath dataNodeWithPath =
                                new DataNodeWithPathImpl(listElementPath,
                                        listElementDataNode);
                        result.add(dataNodeWithPath);
                    }
                }
            }
        } else {
            // Return the list node itself rather than expanding to the
            // matching list elements.
            DataNodeWithPath dataNodeWithPath = new DataNodeWithPathImpl(
                    LocationPathExpression.ofStep(Step.of(listName)), this);
            result.add(dataNodeWithPath);
        }
        return result;
    }

    @Override
    public DataNodeVisitor.Result accept(String name, DataNodeVisitor visitor)
            throws BigDBException {

        Result result = visitor.visitEnterList(name, this);
        if (result == Result.TERMINATE)
            return result;

        if (result != Result.SKIP_SUBTREE) {
            for (DataNode childNode: this) {
                result = childNode.accept((String)null, visitor);
                if ((result == Result.TERMINATE) ||
                        (result == Result.SKIP_SIBLINGS))
                    break;
            }
            if (result == Result.TERMINATE)
                return result;
        }

        result = visitor.visitLeaveList(name, this);

        return result;
    }
}
