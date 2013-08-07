package org.projectfloodlight.db.schema;

import org.projectfloodlight.db.BigDBException;

/**
 * Implementation of the visitor pattern for visiting the nodes
 * in the schema tree.
 * 
 * @author rob.vaterlaus@bigswitch.com
 */
public interface SchemaNodeVisitor {
    
    public enum Result { CONTINUE, SKIP_SIBLINGS, SKIP_SUBTREE, TERMINATE };
    
    /**
     * This is called when we first enter a container schema node before we
     * visit the child nodes of the container node.
     * @param containerSchemaNode
     * @return
     * @throws BigDBException
     */
    public Result visitEnter(ContainerSchemaNode containerSchemaNode)
            throws BigDBException;

    /**
     * This is called when we exit a container schema node after
     * we've visited the child nodes of the container node.
     * @param containerSchemaNode
     * @return
     * @throws BigDBException
     */
    public Result visitLeave(ContainerSchemaNode containerSchemaNode)
            throws BigDBException;
    
    /**
     * This is called when we first enter a list schema node before we visit
     * the child nodes of the list node.
     * @param listSchemaNode
     * @return
     * @throws BigDBException
     */
    public Result visitEnter(ListSchemaNode listSchemaNode)
            throws BigDBException;
    
    /**
     * This is called when we exit a list schema node after we've visited
     * the child nodes of the list node.
     * @param listSchemaNode
     * @return
     * @throws BigDBException
     */
    public Result visitLeave(ListSchemaNode listSchemaNode)
            throws BigDBException;
    
    /**
     * This is called when we first enter a list element schema node before we
     * visit the child nodes of the list node.
     * @param listElementSchemaNode
     * @return
     * @throws BigDBException
     */
    public Result visitEnter(ListElementSchemaNode listElementSchemaNode)
            throws BigDBException;
    
    /**
     * This is called when we exit a list element schema node after we've
     * visited the child nodes of the list node.
     * @param listElementSchemaNode
     * @return
     * @throws BigDBException
     */
    public Result visitLeave(ListElementSchemaNode listElementSchemaNode)
            throws BigDBException;
    
    /**
     * This is called when we first enter a grouping schema node before we
     * visit the child nodes of the grouping node.
     * @param groupingSchemaNode
     * @return
     * @throws BigDBException
     */
    public Result visitEnter(GroupingSchemaNode groupingSchemaNode)
            throws BigDBException;
    
    /**
     * This is called when we exit a grouping schema node after we've
     * visited the child nodes of the grouping node.
     * @param groupingSchemaNode
     * @return
     * @throws BigDBException
     */
    public Result visitLeave(GroupingSchemaNode groupingSchemaNode)
            throws BigDBException;
    
    /**
     * This is called when we visit a leaf schema node.
     * @param leafSchemaNode
     * @return
     * @throws BigDBException
     */
    public Result visit(LeafSchemaNode leafSchemaNode)
            throws BigDBException;
    
    /**
     * This is called when we visit a leaf list schema node.
     * @param leafListSchemaNode
     * @return
     * @throws BigDBException
     */
    public Result visit(LeafListSchemaNode leafListSchemaNode)
            throws BigDBException;
    
    /**
     * This is called when we visit a reference schema node.
     * @param referenceSchemaNode
     * @return
     * @throws BigDBException
     */
    public Result visit(ReferenceSchemaNode referenceSchemaNode)
            throws BigDBException;
    
    /**
     * This is called when we visit a typedef schema node.
     * @param typedefSchemaNode
     * @return
     * @throws BigDBException
     */
    public Result visit(TypedefSchemaNode typedefSchemaNode)
            throws BigDBException;

    /**
     * This is called when we visit a uses schema node.
     * @param usesSchemaNode
     * @throws BigDBException
     */
    public Result visit(UsesSchemaNode usesSchemaNode)
            throws BigDBException;
    
    public Result visit(TypeSchemaNode typeSchemaNode) 
            throws BigDBException;
}
