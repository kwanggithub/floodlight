package net.bigdb.data;

public class DataSourceNotFoundException extends DataSourceException {

    private static final long serialVersionUID = 5194599774121679012L;

    public DataSourceNotFoundException(String dataSourceName) {
        super(dataSourceName, "Data source not found");
    }
}
