package net.bigdb.expression;

import java.util.ArrayList;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.expression.ExpressionVisitor.Result;

/**
 * Models expression can be used to filter node sets.
 * The primary expression must return node set.
 * 
 * @author kevin.wang@bigswitch.com
 *
 */
public class FilterExpression extends PathExpression {
    private Expression primaryExpression;
    private List<Expression> predicates;
    private LocationPathExpression pathExpression;

    public LocationPathExpression getPathExpression() {
        return pathExpression;
    }
    public void setPathExpression(LocationPathExpression pathExpression) {
        this.pathExpression = pathExpression;
    }
    public Expression getPrimaryExpression() {
        return primaryExpression;
    }
    public void setPrimaryExpression(Expression primaryExpression) {
        this.primaryExpression = primaryExpression;
    }
    public List<Expression> getPredicates() {
        return predicates;
    }
    public void setPredicates(List<Expression> predicates) {
        this.predicates = predicates;
    }
    
    public void addPredicate(Expression e) {
        if (this.predicates == null) {
            predicates = new ArrayList<Expression>();
        }
        predicates.add(e);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime *
                        result +
                        ((pathExpression == null) ? 0 : pathExpression
                                .hashCode());
        result =
                prime * result +
                        ((predicates == null) ? 0 : predicates.hashCode());
        result =
                prime *
                        result +
                        ((primaryExpression == null) ? 0 : primaryExpression
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
        FilterExpression other = (FilterExpression) obj;
        if (pathExpression == null) {
            if (other.pathExpression != null)
                return false;
        } else if (!pathExpression.equals(other.pathExpression))
            return false;
        if (predicates == null) {
            if (other.predicates != null)
                return false;
        } else if (!predicates.equals(other.predicates))
            return false;
        if (primaryExpression == null) {
            if (other.primaryExpression != null)
                return false;
        } else if (!primaryExpression.equals(other.primaryExpression))
            return false;
        return true;
    }
    @Override
    public Result accept(ExpressionVisitor visitor) throws BigDBException {
        // TODO: cleanup 
        ExpressionVisitor.Result result = visitor.visitEnter(this);
        if (result == ExpressionVisitor.Result.TERMINATE)
            return ExpressionVisitor.Result.TERMINATE;
        if (primaryExpression != null) {
            result = primaryExpression.accept(visitor);
            if (result == ExpressionVisitor.Result.TERMINATE)
                return ExpressionVisitor.Result.TERMINATE;
        }
        if (pathExpression != null) {
            result = pathExpression.accept(visitor);
            if (result == ExpressionVisitor.Result.TERMINATE)
                return ExpressionVisitor.Result.TERMINATE;
        }
        result = visitor.visitLeave(this);
        return result;        
    }
}
