package org.projectfloodlight.db.data;

import org.projectfloodlight.db.BigDBException;

public class AbstractDataNodeVisitor implements DataNodeVisitor {

    @Override
    public Result visitNull(String name, NullDataNode nullDataNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitNull(IndexValue keyValue, NullDataNode nullDataNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeaf(String name, LeafDataNode leafDataNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitEnterContainer(String name, ContainerDataNode containerDataNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeaveContainer(String name, ContainerDataNode containerDataNode)
            throws BigDBException {
        return Result.CONTINUE;
    }
    
    @Override
    public Result visitEnterList(String name, ListDataNode listDataNode)
            throws BigDBException {
        return Result.CONTINUE;
    }
    
    @Override
    public Result visitLeaveList(String name, ListDataNode listDataNode)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitEnterListElement(IndexValue keyValue,
            ListElementDataNode listElementDataNode) throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeaveListElement(IndexValue keyValue,
            ListElementDataNode listElementDataNode) throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitEnterLeafList(String name,
            LeafListDataNode leafListDataNode) throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeaveLeafList(String name,
            LeafListDataNode leafListDataNode) throws BigDBException {
        return Result.CONTINUE;
    }
}
