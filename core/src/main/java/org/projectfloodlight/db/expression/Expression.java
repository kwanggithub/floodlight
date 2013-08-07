package org.projectfloodlight.db.expression;

import org.projectfloodlight.db.BigDBException;

public interface Expression {
    
    public ExpressionVisitor.Result accept(ExpressionVisitor visitor)
            throws BigDBException;
}
