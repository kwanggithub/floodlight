package net.bigdb.expression;

import net.bigdb.BigDBException;

public class AbstractExpressionVisitor implements ExpressionVisitor {

    @Override
    public Result visit(BooleanLiteralExpression expression)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visit(StringLiteralExpression expression)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visit(IntegerLiteralExpression expression)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visit(DecimalLiteralExpression expression)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visit(DoubleLiteralExpression expression)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visit(VariableExpression expression) {
        return Result.CONTINUE;
    }

    @Override
    public Result visitEnter(UnaryOperatorExpression expression)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeave(UnaryOperatorExpression expression)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitEnter(BinaryOperatorExpression expression)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeave(BinaryOperatorExpression expression)
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitEnter(FilterExpression expression) 
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeave(FilterExpression expression) 
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitEnter(FunctionCallExpression expression)
                throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeave(FunctionCallExpression expression)
               throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitEnter(LocationPathExpression expression)
                    throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeave(LocationPathExpression expression)
                   throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitEnter(UnionExpression expression) 
            throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result visitLeave(UnionExpression expression) throws BigDBException {
        return Result.CONTINUE;
    }
}
