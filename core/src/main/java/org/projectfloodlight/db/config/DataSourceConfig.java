package org.projectfloodlight.db.config;

import java.util.Map;
import java.util.HashMap;

public class DataSourceConfig {

    public static final String INVALID_DATA_SOURCE_NAME_ERROR_MESSAGE =
            "Invalid data source name";

    public static final String INVALID_DATA_SOURCE_IMPLEMENTATION_CLASS_ERROR_MESSAGE =
            "Invalid data source implementation class";


    private static final String DATA_SOURCE_NAME_PATTERN =
            "[A-Za-z_][A-Za-z0-9_\\-.]*";
    // Check this for explanation of the following regex that matches a
    // Java fully qualifed class name:
    // http://stackoverflow.com/questions/5205339/regular-expression-matching-fully-qualified-java-classes
    private static final String CLASS_NAME_PATTERN =
            "([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*";
    
    // Note: The names of these variables must match the names as they
    // appear in the YAML config file for the YAML deserialization code
    // to work properly, which is why we use non-standard variable
    // names with underscores here instead of the normal camel-cased names.
    // The underscore names look a little better in the YAML file.
    public String name;
    public String implementation_class;
    public boolean config;
    public Map<String,String> properties = new HashMap<String,String>();
    
    public DataSourceConfig() {
    }

    public void validate() throws ConfigException {
        // Check that the name is valid
        if ((name == null) || !name.matches(DATA_SOURCE_NAME_PATTERN))
            throw new ConfigException(INVALID_DATA_SOURCE_NAME_ERROR_MESSAGE);
        
        // Check that the implementation class name is valid
        // This just checks that the class name is syntactically correct,
        // not that a class with that name actually exists.
        // We check for that later when try to initialize the data source.
        if ((implementation_class == null) ||
                !implementation_class.matches(CLASS_NAME_PATTERN)) {
            throw new ConfigException(INVALID_DATA_SOURCE_IMPLEMENTATION_CLASS_ERROR_MESSAGE);
        }
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (config ? 1231 : 1237);
        result =
                prime *
                        result +
                        ((implementation_class == null) ? 0
                                : implementation_class.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result =
                prime * result +
                        ((properties == null) ? 0 : properties.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DataSourceConfig other = (DataSourceConfig) obj;
        if (config != other.config)
            return false;
        if (implementation_class == null) {
            if (other.implementation_class != null)
                return false;
        } else if (!implementation_class.equals(other.implementation_class))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (properties == null) {
            if (other.properties != null)
                return false;
        } else if (!properties.equals(other.properties))
            return false;
        return true;
    }
}
