package net.bigdb.expression;

import net.bigdb.BigDBException;

public interface Expression {
    
    public ExpressionVisitor.Result accept(ExpressionVisitor visitor)
            throws BigDBException;
}
