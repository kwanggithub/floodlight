package net.bigdb.schema;

import net.bigdb.BigDBException;

public class InvalidSchemaTypeException extends BigDBException {

    private static final long serialVersionUID = 2941288408421413984L;

    public InvalidSchemaTypeException() {
        this("Invalid schema type");
    }
    
    public InvalidSchemaTypeException(String message) {
        super(message);
    }
    
    public InvalidSchemaTypeException(String message, Throwable cause) {
        super(message, cause);
    }

}
