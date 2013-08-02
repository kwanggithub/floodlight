package net.bigdb.expression;

import net.bigdb.BigDBException;

public class BinaryOperatorExpression implements Expression {

    public enum Operator {
        PLUS("+"), MINUS("-"), MULT("*"), DIV("div"), IDIV("idiv"), MOD("mod"), EQ("="), NE("!="), LT("<"), LE("<="), GT(">"), GE(">="), AND("and"), OR("or");

        private final String representation;
        Operator(String representation) {
            this.representation = representation;
        }
        public String getRepresentation() {
            return representation;
        }


    }

    protected Operator operator;
    protected Expression leftExpression;
    protected Expression rightExpression;

    public BinaryOperatorExpression(Operator op,
            Expression leftExpression,
            Expression rightExpression) {
        this.operator = op;
        this.leftExpression = leftExpression;
        this.rightExpression = rightExpression;
    }

    public Operator getOperator() {
        return operator;
    }

    public Expression getLeftExpression() {
        return leftExpression;
    }

    public Expression getRightExpression() {
        return rightExpression;
    }

    @Override
    public ExpressionVisitor.Result accept(ExpressionVisitor visitor)
            throws BigDBException {
        ExpressionVisitor.Result result = visitor.visitEnter(this);
        if (result == ExpressionVisitor.Result.TERMINATE)
            return ExpressionVisitor.Result.TERMINATE;
        if (leftExpression != null) {
            result = leftExpression.accept(visitor);
            if (result == ExpressionVisitor.Result.TERMINATE)
                return ExpressionVisitor.Result.TERMINATE;
        }
        result = rightExpression.accept(visitor);
        if (result == ExpressionVisitor.Result.TERMINATE)
            return ExpressionVisitor.Result.TERMINATE;
        result = visitor.visitLeave(this);
        return result;
    }

    @Override
    public String toString() {
        return leftExpression.toString() + operator.getRepresentation()+ rightExpression.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime *
                        result +
                        ((leftExpression == null) ? 0 : leftExpression
                                .hashCode());
        result =
                prime * result + ((operator == null) ? 0 : operator.hashCode());
        result =
                prime *
                        result +
                        ((rightExpression == null) ? 0 : rightExpression
                                .hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BinaryOperatorExpression other = (BinaryOperatorExpression) obj;
        if (leftExpression == null) {
            if (other.leftExpression != null)
                return false;
        } else if (!leftExpression.equals(other.leftExpression))
            return false;
        if (operator != other.operator)
            return false;
        if (rightExpression == null) {
            if (other.rightExpression != null)
                return false;
        } else if (!rightExpression.equals(other.rightExpression))
            return false;
        return true;
    }
}
