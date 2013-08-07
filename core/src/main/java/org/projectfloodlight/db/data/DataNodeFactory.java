package org.projectfloodlight.db.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.db.BigDBException;

/**
 * Interface for a factory class that creates the different types of data nodes.
 * Data sources implement this if they implement their own versions of the
 * different types of data nodes.
 * 
 * @author rob.vaterlaus@bigswitch.com
 */
public interface DataNodeFactory {
    
    public ContainerDataNode createContainerDataNode(boolean mutable,
            Map<String, DataNode> values) throws BigDBException;

    public ListDataNode createListDataNode(boolean mutable,
            IndexSpecifier keySpecifier,
            Iterator<DataNode> elements) throws BigDBException;

    public ListElementDataNode createListElementDataNode(boolean mutable,
            Map<String, DataNode> values) throws BigDBException;
    
    public LeafListDataNode createLeafListDataNode(boolean mutable,
            List<DataNode> values) throws BigDBException;
    
    public LeafDataNode createLeafDataNode(boolean value) throws BigDBException;
    
    public LeafDataNode createLeafDataNode(long value) throws BigDBException;
    
    public LeafDataNode createLeafDataNode(BigInteger value)
            throws BigDBException;
    
    public LeafDataNode createLeafDataNode(BigDecimal value)
            throws BigDBException;
    
    public LeafDataNode createLeafDataNode(double value) throws BigDBException;
    
    public LeafDataNode createLeafDataNode(String value) throws BigDBException;

    public LeafDataNode createLeafDataNode(byte[] value) throws BigDBException;
    
    public NullDataNode createNullDataNode() throws BigDBException;
}
