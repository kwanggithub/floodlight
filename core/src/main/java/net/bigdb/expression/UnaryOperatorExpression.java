package net.bigdb.expression;

import net.bigdb.BigDBException;

/**
 * We treat UnaryExpression as a special binaryOperatorExpression
 * with leftExpression = null.
 *
 * @author kevin.wang@bigswitch.com
 *
 */
public class UnaryOperatorExpression implements Expression {

    public enum Operator {
        MINUS("-");

        private final String representation;

        Operator(String representation) {
            this.representation = representation;
        }

        public String getRepresentation() {
            return representation;
        }

    }

    protected Operator operator;
    protected Expression expression;

    public UnaryOperatorExpression(Operator operator, Expression expression) {
        assert operator != null;
        assert expression != null;
        this.operator = operator;
        this.expression = expression;
    }

    public Operator getOperator() {
        return operator;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return operator.getRepresentation() + expression.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result +
                        ((expression == null) ? 0 : expression.hashCode());
        result =
                prime * result + ((operator == null) ? 0 : operator.hashCode());
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
        UnaryOperatorExpression other = (UnaryOperatorExpression) obj;
        if (expression == null) {
            if (other.expression != null)
                return false;
        } else if (!expression.equals(other.expression))
            return false;
        if (operator != other.operator)
            return false;
        return true;
    }

    @Override
    public ExpressionVisitor.Result accept(ExpressionVisitor visitor)
            throws BigDBException {
        ExpressionVisitor.Result result = visitor.visitEnter(this);
        if (result == ExpressionVisitor.Result.TERMINATE)
            return ExpressionVisitor.Result.TERMINATE;
        result = expression.accept(visitor);
        if (result == ExpressionVisitor.Result.TERMINATE)
            return ExpressionVisitor.Result.TERMINATE;
        result = visitor.visitLeave(this);
        return result;
    }
}
