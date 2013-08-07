package org.projectfloodlight.db.schema;

import org.projectfloodlight.db.BigDBException;

public class AbstractSchemaNodeVisitor implements SchemaNodeVisitor {

    @Override
    public Result visitEnter(ContainerSchemaNode containerSchemaNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeave(ContainerSchemaNode containerSchemaNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitEnter(ListSchemaNode listSchemaNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeave(ListSchemaNode listSchemaNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitEnter(ListElementSchemaNode listElementSchemaNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeave(ListElementSchemaNode listElementSchemaNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitEnter(GroupingSchemaNode groupingSchemaNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeave(GroupingSchemaNode groupingSchemaNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visit(LeafSchemaNode leafSchemaNode) throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visit(LeafListSchemaNode leafListSchemaNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visit(ReferenceSchemaNode referenceSchemaNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visit(TypedefSchemaNode typedefSchemaNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visit(UsesSchemaNode usesSchemaNode) 
            throws BigDBException {
        return Result.CONTINUE;
         
    }

    public Result visit(TypeSchemaNode typeSchemaNode) 
            throws BigDBException {
        return Result.CONTINUE;
        
    }
}
