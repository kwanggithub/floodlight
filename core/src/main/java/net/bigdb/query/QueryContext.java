package net.bigdb.query;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import net.bigdb.expression.Expression;
import net.bigdb.query.parser.VariableNotFoundException;
import net.bigdb.query.parser.VariableReplacer;

public class QueryContext implements VariableReplacer {

    private final Map<String, QueryVariable> variables = new HashMap<String, QueryVariable>();

    @Override
    public Expression replace(String name) {
        if(variables.containsKey(name))
            return variables.get(name).getExpression();
        else
            throw VariableNotFoundException.forName(name);
    }

    public QueryContext add(QueryVariable var) {
        variables.put(var.getName(), var);
        return this;
    }

    /** set a string variable to be placed during parsing of LocationPathExressions */
    public QueryContext setVariable(String name, String value) {
        add(QueryVariable.stringVariable(name, value));
        return this;
    }

    /** set a long variable to be placed during parsing of LocationPathExressions */
    public QueryContext setVariable(String name, long value) {
        add(QueryVariable.integerVariable(name, value));
        return this;
    }

    /** set a boolean variable to be placed during parsing of LocationPathExressions */
    public QueryContext setVariable(String name, boolean value) {
        add(QueryVariable.booleanVariable(name, value));
        return this;
    }

    /** set a BigDecimal variable to be placed during parsing of LocationPathExressions */
    public QueryContext setVariable(String name, BigDecimal value) {
        add(QueryVariable.decimalVariable(name, value));
        return this;
    }

}
