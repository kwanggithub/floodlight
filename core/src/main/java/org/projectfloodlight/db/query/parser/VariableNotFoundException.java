package org.projectfloodlight.db.query.parser;

public class VariableNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public VariableNotFoundException(String msg) {
        super(msg);
    }

    public static VariableNotFoundException forName(String name) {
        return new VariableNotFoundException("Cannot replace variable "+name + " - no replacement set");
    }
}
