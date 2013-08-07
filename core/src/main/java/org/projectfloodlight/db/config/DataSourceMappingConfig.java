package org.projectfloodlight.db.config;

public class DataSourceMappingConfig {

    public String predicate;
    public String data_source;
    
    public DataSourceMappingConfig() {
    }

    public void validate() throws ConfigException {
        // Check that a predicate has been specified.
        // We don't check here that it's actually a valid predicate.
        // We'll check that later when we actually try to load the schema data.
        // Allow empty predicate to indicate default mapping.
        //if ((predicate == null) || predicate.isEmpty())
        //    throw new ConfigException("Invalid data source mapping predicate");
        
        // Check that a data source target has been specified.
        // We don't validate that the value corresponds to the name
        // of one of the configured data sources.
        // Again, we'll validate that later
        if ((data_source == null) || data_source.isEmpty())
            throw new ConfigException("Invalid data source target");
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((data_source == null) ? 0 : data_source.hashCode());
        result = prime * result
                + ((predicate == null) ? 0 : predicate.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        DataSourceMappingConfig other = (DataSourceMappingConfig) obj;
        if (data_source == null) {
            if (other.data_source != null) return false;
        } else if (!data_source.equals(other.data_source)) return false;
        if (predicate == null) {
            if (other.predicate != null) return false;
        } else if (!predicate.equals(other.predicate)) return false;
        return true;
    }
}
