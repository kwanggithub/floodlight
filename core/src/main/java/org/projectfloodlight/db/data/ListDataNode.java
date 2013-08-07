package org.projectfloodlight.db.data;

import org.projectfloodlight.db.BigDBException;

/**
 * A data node interface that consists of a list of child nodes.
 *
 * A list can be either keyed or unkeyed.
 *
 * Keyed lists are indexed by an IndexValue, which is the collection of field
 * values that form the primary key for the list elements.
 *
 * Unkeyed lists are ordered by the user and elements can be accessed by an
 * integer index. The operations for unkeyed lists are inherited from the
 * ArrayDataNode interface.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface ListDataNode extends ArrayDataNode {
    public void add(IndexValue indexValue, DataNode dataNode) throws BigDBException;
    public DataNode remove(IndexValue indexValue) throws BigDBException;
}
