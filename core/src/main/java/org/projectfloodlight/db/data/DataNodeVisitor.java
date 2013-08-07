package org.projectfloodlight.db.data;

import org.projectfloodlight.db.BigDBException;

public interface DataNodeVisitor {
    
    public enum Result { CONTINUE, SKIP_SIBLINGS, SKIP_SUBTREE, TERMINATE };

    /**
     * This is called when visiting a null node that's a child of a
     * dictionary data node
     * 
     * @param name
     * @param nullDataNode
     * @return
     * @throws BigDBException
     */
    public Result visitNull(String name, NullDataNode nullDataNode)
            throws BigDBException;

    /**
     * This is called when visiting a null node that's a list element in a
     * list data node
     *
     * @param name
     * @param nullDataNode
     * @return
     * @throws BigDBException
     */
    public Result visitNull(IndexValue keyValue, NullDataNode nullDataNode)
            throws BigDBException;

    /**
     * This is called when visiting a leaf node.
     * 
     * @param name
     * @param leafDataNode
     * @return
     * @throws BigDBException
     */
    public Result visitLeaf(String name, LeafDataNode leafDataNode)
            throws BigDBException;

    /**
     * This is called when entering a container data node before visiting the
     * child nodes.
     * 
     * @param name
     * @param containerDataNode
     * @return
     * @throws BigDBException
     */
    public Result visitEnterContainer(String name,
            ContainerDataNode containerDataNode) throws BigDBException;

    /**
     * This is called when exiting a container data node after visiting the
     * child nodes.
     * 
     * @param name
     * @param containerDataNode
     * @return
     * @throws BigDBException
     */
    public Result visitLeaveContainer(String name,
            ContainerDataNode containerDataNode) throws BigDBException;
    
    /**
     * This is called when entering a list data node before visiting the
     * child nodes.
     * 
     * @param name
     * @param listDataNode
     * @return
     * @throws BigDBException
     */
    public Result visitEnterList(String name, ListDataNode listDataNode)
            throws BigDBException;
    
    /**
     * This is called when exiting a list data node after visiting the
     * child nodes.
     * 
     * @param name
     * @param listDataNode
     * @return
     * @throws BigDBException
     */
    public Result visitLeaveList(String name, ListDataNode listDataNode)
            throws BigDBException;
    
    /**
     * This is called when entering a list element data node before visiting the
     * child nodes.
     * 
     * @param keyValue
     * @param listElementDataNode
     * @return
     * @throws BigDBException
     */
    public Result visitEnterListElement(IndexValue keyValue,
            ListElementDataNode listElementDataNode) throws BigDBException;
    
    /**
     * This is called when exiting a list element data node after visiting the
     * child nodes.
     * 
     * @param keyValue
     * @param listElementDataNode
     * @return
     * @throws BigDBException
     */
    public Result visitLeaveListElement(IndexValue keyValue,
            ListElementDataNode listElementDataNode) throws BigDBException;
    
    /**
     * This is called when entering a leaf list data node before visiting the
     * child nodes.
     * 
     * @param name
     * @param leafListDataNode
     * @return
     * @throws BigDBException
     */
    public Result visitEnterLeafList(String name,
            LeafListDataNode leafListDataNode) throws BigDBException;
    
    /**
     * This is called when exiting a leaf list data node after visiting the
     * child nodes.
     * 
     * @param name
     * @param leafListDataNode
     * @return
     * @throws BigDBException
     */
    public Result visitLeaveLeafList(String name,
            LeafListDataNode leafListDataNode) throws BigDBException;
}
