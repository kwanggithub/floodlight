package net.bigdb.config;

import net.bigdb.BigDBException;

public class ConfigException extends BigDBException {
    
    private static final long serialVersionUID = 3654216282555746596L;

    public ConfigException() {
        super();
    }
    
    public ConfigException(String message) {
        super(message);
    }
    
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
