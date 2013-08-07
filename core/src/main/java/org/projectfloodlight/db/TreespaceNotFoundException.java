package org.projectfloodlight.db;

public class TreespaceNotFoundException extends BigDBException {
    private static final long serialVersionUID = 1L;

    public TreespaceNotFoundException(String treespace) {
        super("Treespace "+treespace+" not found", Type.NOT_FOUND);
    }
}
