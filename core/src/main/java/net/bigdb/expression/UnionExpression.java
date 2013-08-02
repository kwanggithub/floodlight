package net.bigdb.expression;

import java.util.ArrayList;
import java.util.List;

import net.bigdb.BigDBException;

public class UnionExpression implements Expression {
    private List<Expression> expressions;
    
    public List<Expression> getExpressions() {
        return expressions;
    }

    public void setExpressions(List<Expression> expressions) {
        this.expressions = expressions;
    }
    
    public void addExpression(Expression expression) {
        if (expressions == null) {
            expressions = new ArrayList<Expression>();
        }
        expressions.add(expression);
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result +
                        ((expressions == null) ? 0 : expressions.hashCode());
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
        UnionExpression other = (UnionExpression) obj;
        if (expressions == null) {
            if (other.expressions != null)
                return false;
        } else if (!expressions.equals(other.expressions))
            return false;
        return true;
    }

    @Override
    public ExpressionVisitor.Result accept(ExpressionVisitor visitor)
            throws BigDBException {
        ExpressionVisitor.Result result = visitor.visitEnter(this);
        if (result == ExpressionVisitor.Result.TERMINATE)
            return ExpressionVisitor.Result.TERMINATE;
        if (expressions != null) {
            for (Expression e : this.getExpressions()) {
                if (e.accept(visitor) == ExpressionVisitor.Result.TERMINATE) {
                    return ExpressionVisitor.Result.TERMINATE;
                }
            }
        }
        result = visitor.visitLeave(this);
        return result;
    }
}
