package org.projectfloodlight.db.data;

import java.util.Iterator;

import org.projectfloodlight.db.BigDBException;

public interface ArrayDataNode extends DataNode {
    public void add(DataNode dataNode) throws BigDBException;
    public void add(int index, DataNode dataNode) throws BigDBException;
    public void addAll(Iterable<DataNode> dataNodes) throws BigDBException;
    public void addAll(Iterator<DataNode> dataNodes) throws BigDBException;
    public DataNode remove(int index) throws BigDBException;
}
