package org.projectfloodlight.db.data;

import org.projectfloodlight.db.BigDBException;

public interface DictionaryDataNode extends DataNode {
    public void put(String name, DataNode dataNode) throws BigDBException;
    public DataNode remove(String name) throws BigDBException;
}
