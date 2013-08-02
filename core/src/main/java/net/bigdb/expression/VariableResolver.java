package net.bigdb.expression;

public interface VariableResolver {
    Object resolveVariable(String type, String name);
}
