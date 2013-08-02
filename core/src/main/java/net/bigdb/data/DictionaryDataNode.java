package net.bigdb.data;

import net.bigdb.BigDBException;

public interface DictionaryDataNode extends DataNode {
    public void put(String name, DataNode dataNode) throws BigDBException;
    public DataNode remove(String name) throws BigDBException;
}
