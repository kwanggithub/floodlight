package org.projectfloodlight.db.schema;

import org.projectfloodlight.db.BigDBException;

public class SchemaNodeTypeConflictException extends BigDBException {

    private static final long serialVersionUID = -2285745097668304700L;

    public SchemaNodeTypeConflictException() {
        this("Conflicting schema node types from different schema files/modules");
    }
    
    public SchemaNodeTypeConflictException(String message) {
        super(message);
    }
    
    public SchemaNodeTypeConflictException(String message, Throwable cause) {
        super(message, cause);
    }

}
