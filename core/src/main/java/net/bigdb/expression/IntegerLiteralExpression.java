package net.bigdb.expression;

import java.math.BigInteger;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;

public class IntegerLiteralExpression implements Expression {
    
    private Long longValue;
    private BigInteger bigIntegerValue;

    public IntegerLiteralExpression(Long value) {
        this.longValue = value;
        this.bigIntegerValue = BigInteger.valueOf(value);
    }
    
    public IntegerLiteralExpression(BigInteger value) {
        this.bigIntegerValue = value;
        this.longValue = value.longValue();
    }

    public IntegerLiteralExpression(String value) throws BigDBException {
        try {
            bigIntegerValue = new BigInteger(value);
        }
        catch (NumberFormatException exc1) {
            throw new BigDBException("Invalid integer literal string");
        }
        
        try {
            longValue = Long.valueOf(value);
        }
        catch (NumberFormatException exc) {
        }
        
    }
    
    public Object getValue() {
        return (longValue != null) ? longValue : bigIntegerValue;
    }
    
    public Long getLongValue() {
        return longValue;
    }

    public BigInteger getBigIntegerValue() {
        return bigIntegerValue;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime *
                        result +
                        ((bigIntegerValue == null) ? 0 : bigIntegerValue
                                .hashCode());
        result =
                prime * result +
                        ((longValue == null) ? 0 : longValue.hashCode());
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
        IntegerLiteralExpression other = (IntegerLiteralExpression) obj;
        if (bigIntegerValue == null) {
            if (other.bigIntegerValue != null)
                return false;
        } else if (!bigIntegerValue.equals(other.bigIntegerValue))
            return false;
        if (longValue == null) {
            if (other.longValue != null)
                return false;
        } else if (!longValue.equals(other.longValue))
            return false;
        return true;
    }

    @Override
    public String toString() {
        if (longValue != null)
            return longValue.toString();
        if (bigIntegerValue != null)
            return bigIntegerValue.toString();
        throw new BigDBInternalError("Invalid integer literal value");
    }

    public ExpressionVisitor.Result accept(ExpressionVisitor visitor)
            throws BigDBException {
        ExpressionVisitor.Result result = visitor.visit(this);
        return result;
    }
}
