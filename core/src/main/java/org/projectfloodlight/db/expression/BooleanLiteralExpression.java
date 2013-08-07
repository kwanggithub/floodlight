package org.projectfloodlight.db.expression;

import org.projectfloodlight.db.BigDBException;

public class BooleanLiteralExpression implements Expression {

    private Boolean value;

    public BooleanLiteralExpression(Boolean value) {
        this.value = value;
    }
    
    public BooleanLiteralExpression(String value) {
        this.value = Boolean.valueOf(value);
    }
    
    public Boolean getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((value == null) ? 0 : value.hashCode());
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
        BooleanLiteralExpression other = (BooleanLiteralExpression) obj;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

    @Override
    public ExpressionVisitor.Result accept(ExpressionVisitor visitor)
            throws BigDBException {
        ExpressionVisitor.Result result = visitor.visit(this);
        return result;
    }
}
