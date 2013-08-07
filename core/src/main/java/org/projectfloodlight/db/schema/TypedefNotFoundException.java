package org.projectfloodlight.db.schema;

import org.projectfloodlight.db.BigDBException;

public class TypedefNotFoundException extends BigDBException {

    private static final long serialVersionUID = -3296408091616645151L;

    public TypedefNotFoundException() {
        super();
    }
    
    public TypedefNotFoundException(String typeName) {
        this(typeName, null);
    }
    
    public TypedefNotFoundException(String typeName, Throwable cause) {
        super("Type definition not found: " + typeName, cause);
    }
}
