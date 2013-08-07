package org.projectfloodlight.db.expression;

public interface VariableResolver {
    Object resolveVariable(String type, String name);
}
