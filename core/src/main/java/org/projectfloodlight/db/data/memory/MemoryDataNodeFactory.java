package org.projectfloodlight.db.data.memory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.ContainerDataNode;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeFactory;
import org.projectfloodlight.db.data.IndexSpecifier;
import org.projectfloodlight.db.data.LeafDataNode;
import org.projectfloodlight.db.data.LeafListDataNode;
import org.projectfloodlight.db.data.ListDataNode;
import org.projectfloodlight.db.data.ListElementDataNode;
import org.projectfloodlight.db.data.NullDataNode;

/**
 * Implementation of the DataNodeFactory interface that creates in-memory
 * implementations of the different data nodes types.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public class MemoryDataNodeFactory implements DataNodeFactory {

    @Override
    public ContainerDataNode createContainerDataNode(boolean mutable,
            Map<String, DataNode> values) throws BigDBException {
        return new MemoryContainerDataNode(mutable, values);
    }

    @Override
    public ListDataNode createListDataNode(boolean mutable,
            IndexSpecifier keySpecifier,
            Iterator<DataNode> elements) throws BigDBException {
        return (keySpecifier != null) ?
                new MemoryKeyedListDataNode(mutable, keySpecifier, elements) :
                new MemoryUnkeyedListDataNode(mutable, elements);
    }

    @Override
    public ListElementDataNode createListElementDataNode(boolean mutable,
            Map<String, DataNode> values) throws BigDBException {
        return new MemoryListElementDataNode(mutable, values);
    }

    @Override
    public LeafListDataNode createLeafListDataNode(boolean mutable,
            List<DataNode> values) throws BigDBException {
        return new MemoryLeafListDataNode(mutable, values);
    }

    @Override
    public LeafDataNode createLeafDataNode(boolean value)
            throws BigDBException {
        return new MemoryLeafDataNode(value);
    }

    @Override
    public LeafDataNode createLeafDataNode(long value) throws BigDBException {
        return new MemoryLeafDataNode(value);
    }

    @Override
    public LeafDataNode createLeafDataNode(BigInteger value)
            throws BigDBException {
        return new MemoryLeafDataNode(value);
    }

    @Override
    public LeafDataNode createLeafDataNode(BigDecimal value)
            throws BigDBException {
        return new MemoryLeafDataNode(value);
    }

    @Override
    public LeafDataNode createLeafDataNode(double value) throws BigDBException {
        return new MemoryLeafDataNode(value);
    }

    @Override
    public LeafDataNode createLeafDataNode(String value) throws BigDBException {
        return new MemoryLeafDataNode(value);
    }

    @Override
    public LeafDataNode createLeafDataNode(byte[] value) throws BigDBException {
        return new MemoryLeafDataNode(value);
    }

    @Override
    public NullDataNode createNullDataNode() throws BigDBException {
        return new NullDataNode();
    }
}
