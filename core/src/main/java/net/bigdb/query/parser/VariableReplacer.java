package net.bigdb.query.parser;

import net.bigdb.expression.Expression;

public interface VariableReplacer {
    public final VariableReplacer NONE = new FailReplacer();

    public Expression replace(String name);

    static class FailReplacer implements VariableReplacer {
        @Override
        public Expression replace(String name) {
            throw VariableNotFoundException.forName(name);
        }

    }
}
