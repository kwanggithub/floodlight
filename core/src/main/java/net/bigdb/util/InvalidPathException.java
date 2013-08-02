package net.bigdb.util;

import net.bigdb.BigDBException;

public class InvalidPathException extends BigDBException {
    
    private static final long serialVersionUID = -1795589822565385820L;

    public InvalidPathException() {
        super();
    }
    
    public InvalidPathException(String message) {
        super(message);
    }
    
    public InvalidPathException(String message, Throwable cause) {
        super(message, cause);
    }
}
