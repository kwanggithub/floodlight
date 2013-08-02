package net.bigdb.config;

import java.util.ArrayList;
import java.util.List;

public class TreespaceConfig {

    public static final String INVALID_TREESPACE_NAME_ERROR_MESSAGE =
            "Invalid treespace name";
    
    // Note: The names of these variables must match the names as they
    // appear in the YAML config file for the YAML deserialization code
    // to work properly, which is why we use non-standard variable
    // names with underscores here instead of the normal camel-cased names.
    // The underscore names look a little better in the YAML file.
    public String name;
    public List<DataSourceConfig> data_sources =
            new ArrayList<DataSourceConfig>();
    public List<DataSourceMappingConfig> data_source_mappings =
            new ArrayList<DataSourceMappingConfig>();
    public List<ModuleSearchPathConfig> module_search_paths =
            new ArrayList<ModuleSearchPathConfig>();
    public List<ModuleConfig> modules =
            new ArrayList<ModuleConfig>();
    
    public TreespaceConfig() {
    }

    private static final String TREESPACE_NAME_PATTERN =
            "[A-Za-z_][A-Za-z0-9_\\-.]*";

    public void validate() throws ConfigException {
        // Check that the name is valid
        if ((name == null) || !name.matches(TREESPACE_NAME_PATTERN))
            throw new ConfigException(INVALID_TREESPACE_NAME_ERROR_MESSAGE);
        
        // Check that all of the data source configurations are valid
        if (data_sources != null) {
            for (DataSourceConfig dataSource: data_sources) {
                dataSource.validate();
            }
        }
        
        // Check that all of the data source mapping configurations are valid
        if (data_source_mappings != null) {
            for (DataSourceMappingConfig dataSourceMapping: data_source_mappings) {
                dataSourceMapping.validate();
            }
        }
        
        // Check that all of the module search paths are valid
        if (module_search_paths != null) {
            for (ModuleSearchPathConfig moduleSearchPath: module_search_paths) {
                moduleSearchPath.validate();
            }
        }
        
        // Check that all of the schema locations are valid
        if (modules != null) {
            for (ModuleConfig module: modules) {
                module.validate();
            }
        }
    }
    
    public void merge(TreespaceConfig config) {
        data_sources.addAll(config.data_sources);
        data_source_mappings.addAll(config.data_source_mappings);
        module_search_paths.addAll(config.module_search_paths);
        modules.addAll(config.modules);        
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime
                * result
                + ((data_source_mappings == null) ? 0 : data_source_mappings
                        .hashCode());
        result = prime * result
                + ((data_sources == null) ? 0 : data_sources.hashCode());
        result = prime
                * result
                + ((module_search_paths == null) ? 0 : module_search_paths
                        .hashCode());
        result = prime * result
                + ((modules == null) ? 0 : modules.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        TreespaceConfig other = (TreespaceConfig) obj;
        if (data_source_mappings == null) {
            if (other.data_source_mappings != null) return false;
        } else if (!data_source_mappings.equals(other.data_source_mappings))
            return false;
        if (data_sources == null) {
            if (other.data_sources != null) return false;
        } else if (!data_sources.equals(other.data_sources)) return false;
        if (module_search_paths == null) {
            if (other.module_search_paths != null) return false;
        } else if (!module_search_paths.equals(other.module_search_paths))
            return false;
        if (modules == null) {
            if (other.modules != null) return false;
        } else if (!modules.equals(other.modules)) return false;
        if (name == null) {
            if (other.name != null) return false;
        } else if (!name.equals(other.name)) return false;
        return true;
    }
}
