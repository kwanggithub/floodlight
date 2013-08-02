package net.bigdb.query;

import java.math.BigDecimal;

import net.bigdb.expression.BooleanLiteralExpression;
import net.bigdb.expression.DecimalLiteralExpression;
import net.bigdb.expression.Expression;
import net.bigdb.expression.IntegerLiteralExpression;
import net.bigdb.expression.StringLiteralExpression;

public class QueryVariable {

    private final Expression expression;
    private final String name;

    public QueryVariable(String name, Expression expression) {
        this.name = name;
        this.expression = expression;
    }

    public Expression getExpression() {
        return expression;
    }

    public String getName() {
        return name;
    }

    public static QueryVariable stringVariable(String name, String value) {
        return new QueryVariable(name, new StringLiteralExpression(value));
    }

    public static QueryVariable integerVariable(String name, long value) {
        return new QueryVariable(name, new IntegerLiteralExpression(value));
    }

    public static QueryVariable booleanVariable(String name, boolean value) {
        return new QueryVariable(name, new BooleanLiteralExpression(value));
    }

    public static QueryVariable decimalVariable(String name, BigDecimal value) {
        return new QueryVariable(name, new DecimalLiteralExpression(value));
    }
}
