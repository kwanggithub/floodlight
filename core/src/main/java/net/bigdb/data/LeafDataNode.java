package net.bigdb.data;

import net.bigdb.BigDBException;

public interface LeafDataNode extends DataNode {

    public enum LeafType {
        BOOLEAN,
        LONG,
        BIG_INTEGER,
        BIG_DECIMAL,
        DOUBLE,
        STRING,
        BINARY
    }

    // Methods for leaf data nodes
    public LeafType getLeafType() throws BigDBException;
}
