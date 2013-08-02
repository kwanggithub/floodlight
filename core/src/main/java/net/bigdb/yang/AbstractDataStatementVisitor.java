package net.bigdb.yang;

import net.bigdb.BigDBException;

public class AbstractDataStatementVisitor implements DataStatementVisitor {

    @Override
    public void visitEnter(ContainerStatement containerNode)
            throws BigDBException {
    }

    @Override
    public void visitLeave(ContainerStatement containerNode)
            throws BigDBException {
    }

    @Override
    public void visitEnter(ListStatement listNode) throws BigDBException {
    }

    @Override
    public void visitLeave(ListStatement listNode) throws BigDBException {
    }

    @Override
    public void visit(LeafStatement leafNode) throws BigDBException {
    }

    @Override
    public void visit(LeafListStatement leafListNode) throws BigDBException {
    }

    @Override
    public void visit(UsesStatement usesNode) throws BigDBException {
        
    }

    @Override
    public void visit(UnknownStatement node) throws BigDBException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void visit(ExtensionStatement node) throws BigDBException {
        // TODO Auto-generated method stub
        
    }
}
