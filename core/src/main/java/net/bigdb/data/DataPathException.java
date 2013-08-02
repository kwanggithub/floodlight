package net.bigdb.data;

import net.bigdb.BigDBException;

public class DataPathException extends BigDBException {

    private static final long serialVersionUID = 8670286106173399299L;

    private static String getFullMessageString(String path, String message) {
        String fullMessage = "Invalid data path: \"" + path + "\"";
        if (message != null)
            fullMessage = fullMessage + "; " + message;
        return fullMessage;
    }
    
    public DataPathException(String path) {
        super(getFullMessageString(path, null));
    }
    
    public DataPathException(String path, String message) {
        super(getFullMessageString(path, message));
    }
}
