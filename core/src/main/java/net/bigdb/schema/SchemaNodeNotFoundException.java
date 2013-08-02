package net.bigdb.schema;

import net.bigdb.BigDBException;

public class SchemaNodeNotFoundException extends BigDBException {

    private static final long serialVersionUID = -9056203577942869365L;
    
    private String path;
    private String details;
    
    private static String getFullMessageString(String path,
            String details) {
        String fullMessage = "Schema path not found: \"" + path + "\"";
        if (details != null)
            fullMessage = fullMessage + "; " + details;
        return fullMessage;
    }
    
    public SchemaNodeNotFoundException(String path) {
        super(getFullMessageString(path, null));
        this.path = path;
    }
    
    public SchemaNodeNotFoundException(String path, String details) {
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
