package net.bigdb.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeVisitor.Result;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.query.Step;
import net.bigdb.schema.SchemaNode;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=AbstractDataNode.ArraySerializer.class)
public abstract class AbstractLeafListDataNode extends AbstractDataNode
        implements LeafListDataNode {

    @Override
    public NodeType getNodeType() {
        return NodeType.LEAF_LIST;
    }

    @Override
    public DataNodeVisitor.Result accept(String name, DataNodeVisitor visitor)
            throws BigDBException {

        Result result = visitorEnter(name, visitor);
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

        result = visitorLeave(name, visitor);

        return result;
    }

    private Result visitorLeave(String name, DataNodeVisitor visitor) throws BigDBException {
        Result result = visitor.visitLeaveLeafList(name, this);
        return result;
    }

    private Result visitorEnter(String name, DataNodeVisitor visitor) throws BigDBException {
        Result result = visitor.visitEnterLeafList(name, this);
        return result;
    }

    @Override
    public void addAll(Iterable<DataNode> dataNodes) throws BigDBException {
        if (dataNodes != null)
            addAll(dataNodes.iterator());
    }

    @Override
    public void addAll(Iterator<DataNode> dataNodes) throws BigDBException {
        if (dataNodes != null) {
            while (dataNodes.hasNext())
                add(dataNodes.next());
        }
    }

    @Override
    public DigestValue computeDigestValue() throws BigDBException {
        DigestValue.Builder builder = new DigestValue.Builder();
        builder.update(getNodeType().name() + "|");
        for (DataNode leafDataNode: this) {
            builder.update(String.format("%s|",
                    leafDataNode.getDigestValue().toString()));
        }
        return builder.getDigestValue();
    }

    @Override
    public boolean isIterable() {
        return true;
    }

    @Override
    public boolean isArray() {
        return true;
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


        // We're at a leaf so we should be at the end of the query path
        if (queryPath.size() > 1) {
            throw new BigDBException("Invalid path argument: " + queryPath);
        }

        Step listStep = queryPath.getStep(0);

        List<DataNodeWithPath> result = new ArrayList<DataNodeWithPath>();
        if (expandTrailingList) {
            // Currently we only support querying the entire leaf list or else
            // a single leaf element, so check to see if there's a
            // non-negative integer index predicate.
            int index = 0;
            Iterable<DataNode> dataNodes;
            if (listStep.getPredicates().isEmpty()) {
                dataNodes = this;
            } else {
                index = listStep.getIndexPredicate();
                if (index < 0) {
                    throw new BigDBException(
                            "Invalid predicate for array-like data node: " +
                                    listStep);
                }
                DataNode dataNode = getChild(index);
                dataNodes =
                        dataNode.isNull() ? Collections.<DataNode> emptyList()
                                : Collections.singleton(dataNode);
            }

            for (DataNode dataNode: dataNodes) {
                LocationPathExpression leafElementPath =
                        DataNodeUtilities.getListElementLocationPath(queryPath, index++);
                DataNodeWithPath dataNodeWithPath =
                        new DataNodeWithPathImpl(leafElementPath, dataNode);
                result.add(dataNodeWithPath);
            }
        } else {
            // Return the list node itself rather than expanding to the
            // matching list elements.
            DataNodeWithPath dataNodeWithPath = new DataNodeWithPathImpl(
                    LocationPathExpression.ofStep(Step.of(listStep.getName())),
                    this);
            result.add(dataNodeWithPath);
        }

        return result;
    }

    @Override
    public int childCount() throws BigDBException {
        // FIXME: This is pretty inefficient because we're incurring the
        // overhead of iterating through all of the element data nodes,
        // but it's going to be inefficient anyway, so clients really shouldn't
        // be using this.
        Iterator<DataNode> iter = iterator();
        int count = 0;
        while (iter.hasNext()) {
            iter.next();
            count++;
        }
        return count;
    }

    @Override
    public boolean hasChildren() throws BigDBException {
        return iterator().hasNext();
    }

    @Override
    public void add(DataNode dataNode) throws BigDBException {
        throw new UnsupportedOperationException("Leaf list implementation " +
                this.getClass() + " does not support add operation");
    }

    @Override
    public void add(int index, DataNode dataNode) throws BigDBException {
        throw new UnsupportedOperationException("Leaf list implementation " +
                this.getClass() + " does not support add operation");
    }

    @Override
    public DataNode remove(int index) throws BigDBException {
        throw new UnsupportedOperationException("Leaf list implementation " +
                this.getClass() + " does not support remove operation");
    }
}
