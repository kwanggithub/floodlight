package org.projectfloodlight.db.query.parser;

import org.projectfloodlight.db.expression.Expression;

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
