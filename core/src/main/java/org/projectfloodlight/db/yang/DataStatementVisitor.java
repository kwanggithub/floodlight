package org.projectfloodlight.db.yang;

import org.projectfloodlight.db.BigDBException;

/**
 * Implementation of the visitor pattern for visiting the data nodes
 * in a YANG module container hierarchy.
 * 
 * @author rob.vaterlaus@bigswitch.com
 */
public interface DataStatementVisitor {
    /**
     * This is called when we first enter a container node before we visit
     * the child nodes of the container node.
     * @param containerNode
     * @throws BigDBException
     */
    public void visitEnter(ContainerStatement containerNode) throws BigDBException;
    
    /**
     * This is called when we exit a container node after we've visited
     * the child nodes of the container node.
     * @param containerNode
     * @throws BigDBException
     */
    public void visitLeave(ContainerStatement containerNode) throws BigDBException;
    
    /**
     * This is called when we first enter a list node before we visit
     * the child nodes of the list node.
     * @param listNode
     * @throws BigDBException
     */
    public void visitEnter(ListStatement listNode) throws BigDBException;

    /**
     * This is called when we exit a list node after we've visited
     * the child nodes of the list node.
     * @param listNode
     * @throws BigDBException
     */
    public void visitLeave(ListStatement listNode) throws BigDBException;
    
    /**
     * This is called when we visit a leaf node.
     * @param leafNode
     * @throws BigDBException
     */
    public void visit(LeafStatement leafNode) throws BigDBException;
    
    /**
     * This is called when we visit a leaf list node.
     * @param leafListNode
     * @throws BigDBException
     */
    public void visit(LeafListStatement leafListNode) throws BigDBException;

    /**
     * This is called when we visit a uses node.
     * @param usesNode
     * @throws BigDBException
     */
    public void visit(UsesStatement usesNode) throws BigDBException;

    /**
     * This is called when we visit a Unknown statement node.
     * @param node
     * @throws BigDBException
     */
    public void visit(UnknownStatement node) throws BigDBException;
    
    /**
     * This is called when we visit a extension statement node.
     * @param node
     * @throws BigDBException
     */
    public void visit(ExtensionStatement node) throws BigDBException;    
}
