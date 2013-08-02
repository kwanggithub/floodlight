package net.bigdb.expression;

import net.bigdb.BigDBException;

public class VariableExpression implements Expression {
    
    protected String type;
    protected String name;
    
    public VariableExpression(String type, String name) {
        this.type = type;
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public String getName() {
        return name;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
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
        VariableExpression other = (VariableExpression) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (type == null) {
            if (other.type != null)
                return false;
        } else if (!type.equals(other.type))
            return false;
        return true;
    }

    public ExpressionVisitor.Result accept(ExpressionVisitor visitor)
            throws BigDBException {
        ExpressionVisitor.Result result = visitor.visit(this);
        return result;
    }
}
