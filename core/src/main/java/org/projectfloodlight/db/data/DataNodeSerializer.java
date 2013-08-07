package org.projectfloodlight.db.data;

import org.projectfloodlight.db.BigDBException;

public interface DataNodeSerializer<T> {
    public void serialize(T object, DataNodeGenerator generator)
            throws BigDBException;
}
