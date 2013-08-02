package net.bigdb.data;

import net.bigdb.BigDBException;

public interface DataNodeSerializer<T> {
    public void serialize(T object, DataNodeGenerator generator)
            throws BigDBException;
}
