package org.projectfloodlight.db.data;

import java.util.Collections;
import java.util.Iterator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Iterables;

/**
 * Represents an ordered list of data nodes returned from a query operation for
 * a treespace. Currently this is really just a lightweight wrapper over the
 * Iterable<DataNode> result that's used internally in the BigDB core, but it
 * adds the getSingleDataNode utility function, which is useful from client
 * code.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public final class DataNodeSet implements Iterable<DataNode> {

    public static final DataNodeSet EMPTY_DATA_NODE_SET = new DataNodeSet();

    private final Iterable<DataNode> dataNodes;

    public DataNodeSet() {
        dataNodes = Collections.emptyList();
    }

    public DataNodeSet(Iterable<DataNode> dataNodes) {
        this.dataNodes = dataNodes;
    }

    @Override
    public Iterator<DataNode> iterator() {
        return dataNodes.iterator();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return !dataNodes.iterator().hasNext();
    }

    @JsonIgnore
    public DataNode getSingleDataNode() {
        Iterator<DataNode> iter = dataNodes.iterator();
        if (!iter.hasNext())
            return DataNode.NULL;
        DataNode dataNode = iter.next();
        if (iter.hasNext())
            throw new IllegalStateException(
                    "getSingleDataNode call invalid for DataNodeSet with multiple data nodes");
        return dataNode;
    }

    @Override
    public String toString() {
        return Iterables.toString(this);
    }
}
