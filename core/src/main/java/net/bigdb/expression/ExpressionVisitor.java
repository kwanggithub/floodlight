package net.bigdb.expression;

import net.bigdb.BigDBException;

public interface ExpressionVisitor {
    
    public enum Result { CONTINUE, TERMINATE }
    
    public ExpressionVisitor.Result visit(BooleanLiteralExpression expression)
            throws BigDBException;
    
    public ExpressionVisitor.Result visit(StringLiteralExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visit(IntegerLiteralExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visit(DecimalLiteralExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visit(DoubleLiteralExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visit(VariableExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visitEnter(UnaryOperatorExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visitLeave(UnaryOperatorExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visitEnter(BinaryOperatorExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visitLeave(BinaryOperatorExpression expression)
            throws BigDBException;
    
    public ExpressionVisitor.Result visitEnter(FilterExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visitLeave(FilterExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visitEnter(FunctionCallExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visitLeave(FunctionCallExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visitEnter(LocationPathExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visitLeave(LocationPathExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visitEnter(UnionExpression expression)
            throws BigDBException;

    public ExpressionVisitor.Result visitLeave(UnionExpression expression)
            throws BigDBException;
    
}