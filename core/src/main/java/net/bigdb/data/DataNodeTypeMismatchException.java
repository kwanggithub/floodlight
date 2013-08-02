package net.bigdb.data;

import net.bigdb.BigDBException;

public class DataNodeTypeMismatchException extends BigDBException {
    
    private static final long serialVersionUID = 6591231000339084703L;
    
    public DataNodeTypeMismatchException() {
        super("Data node type mismatch with requested operation");
    }
    
    public DataNodeTypeMismatchException(String message) {
        super(message);
    }
}
