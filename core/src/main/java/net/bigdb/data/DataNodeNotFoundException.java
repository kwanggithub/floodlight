package net.bigdb.data;

import net.bigdb.BigDBException;

public class DataNodeNotFoundException extends BigDBException {

    private static final long serialVersionUID = 61050706638017813L;
    
    private String path;
    private String details;
    
    private static String getFullMessageString(String path,
            String details) {
        String fullMessage = "Schema path not found: \"" + path + "\"";
        if (details != null)
            fullMessage = fullMessage + "; " + details;
        return fullMessage;
    }
    
    public DataNodeNotFoundException(String path) {
        super(getFullMessageString(path, null));
        this.path = path;
    }
    
    public DataNodeNotFoundException(String path, String details) {
        super(getFullMessageString(path, details));
        this.path = path;
        this.details = details;
    }
    
    public String getPath() {
        return path;
    }
    
    public String getDetails() {
        return details;
    }
}
