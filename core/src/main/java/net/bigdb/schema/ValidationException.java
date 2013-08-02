package net.bigdb.schema;

import net.bigdb.BigDBException;

public class ValidationException extends BigDBException {
    
    private static final long serialVersionUID = 5105674113325819509L;

    private static final String VALIDATION_ERROR_MESSAGE = "Validation failed";

    public ValidationException() {
        super(VALIDATION_ERROR_MESSAGE, BigDBException.Type.BAD_CLIENT_REQUEST);
    }
    
    public ValidationException(String message) {
        super(buildMessage(VALIDATION_ERROR_MESSAGE, message),
                BigDBException.Type.BAD_CLIENT_REQUEST);
    }
    
    public ValidationException(String message, Throwable cause) {
        super(buildMessage(VALIDATION_ERROR_MESSAGE, message),
                BigDBException.Type.BAD_CLIENT_REQUEST, cause);
    }
}
