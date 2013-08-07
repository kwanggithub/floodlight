package org.projectfloodlight.db.schema;

import org.projectfloodlight.db.BigDBException;

public class InvalidSchemaPathException extends BigDBException {

    private static final long serialVersionUID = 34121391973068378L;

    private static String getFullMessageString(String schemaPath, String message) {
        String fullMessage = "Invalid schema path: \"" + schemaPath + "\"";
        if (message != null)
            fullMessage = fullMessage + "; " + message;
        return fullMessage;
    }
    
    public InvalidSchemaPathException(String schemaPath) {
        super(getFullMessageString(schemaPath, null));
    }
    
    public InvalidSchemaPathException(String schemaPath, String message) {
        super(getFullMessageString(schemaPath, message));
    }
}
