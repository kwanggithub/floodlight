package net.bigdb.data;

import net.bigdb.BigDBException;

public class DataSourceException extends BigDBException {

    private static final long serialVersionUID = -172430868602133651L;
    
    private String dataSourceName;
    
    private static String getFullMessageString(String dataSourceName,
            String details) {
        String fullMessage = "Error with data source: \"" + dataSourceName + "\"";
        if (details != null)
            fullMessage = fullMessage + "; " + details;
        return fullMessage;
    }
    
    public DataSourceException(String dataSourceName, String message) {
        super(getFullMessageString(dataSourceName, message));
        this.dataSourceName = dataSourceName;
    }

    public DataSourceException(String dataSourceName, String message,
            Throwable cause) {
        super(getFullMessageString(dataSourceName, null), cause);
        this.dataSourceName = dataSourceName;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }
}
