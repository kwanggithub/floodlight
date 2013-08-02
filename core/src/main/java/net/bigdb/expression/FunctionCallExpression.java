package net.bigdb.expression;

import java.util.ArrayList;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.expression.ExpressionVisitor.Result;

public class FunctionCallExpression implements Expression {
    private String functionName;
    private List<Expression> args;
    
    public FunctionCallExpression(String functionName) {
        this.functionName = functionName;
    }
    
    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public List<Expression> getArgs() {
        return args;
    }

    public void setArgs(List<Expression> args) {
        this.args = args;
    }
    
    public void addArgument(Expression e) {
        if (args == null) {
            args = new ArrayList<Expression>();
        }
        args.add(e);
    }
   
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((args == null) ? 0 : args.hashCode());
        result =
                prime * result +
                        ((functionName == null) ? 0 : functionName.hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(functionName);
        builder.append('(');
        if (args != null) {
            boolean firstTime = true;
            for (Expression arg: args) {
                if (firstTime)
                    firstTime = false;
                else
                    builder.append(',');
                builder.append(arg.toString());
            }
        }
        builder.append(')');
        return builder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FunctionCallExpression other = (FunctionCallExpression) obj;
        if (args == null) {
            if (other.args != null)
                return false;
        } else if (!args.equals(other.args))
            return false;
        if (functionName == null) {
            if (other.functionName != null)
                return false;
        } else if (!functionName.equals(other.functionName))
            return false;
        return true;
    }

    @Override
    public Result accept(ExpressionVisitor visitor) throws BigDBException {
        Result result = visitor.visitEnter(this);
        if (result == Result.TERMINATE)
            return Result.TERMINATE;
        return visitor.visitLeave(this);
    }

}
