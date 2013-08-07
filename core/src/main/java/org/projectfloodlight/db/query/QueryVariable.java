package org.projectfloodlight.db.query;

import java.math.BigDecimal;

import org.projectfloodlight.db.expression.BooleanLiteralExpression;
import org.projectfloodlight.db.expression.DecimalLiteralExpression;
import org.projectfloodlight.db.expression.Expression;
import org.projectfloodlight.db.expression.IntegerLiteralExpression;
import org.projectfloodlight.db.expression.StringLiteralExpression;

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
