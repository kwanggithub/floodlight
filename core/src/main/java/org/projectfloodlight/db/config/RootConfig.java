package org.projectfloodlight.db.config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.projectfloodlight.db.auth.AuthConfig;
import org.yaml.snakeyaml.Yaml;

public class RootConfig {
    public static final String LOAD_DESERIALIZE_ERROR_MESSAGE =
            "Error parsing/deserializing config data";
    public static final String CONFIG_FILE_NOT_FOUND_ERROR_MESSAGE =
            "Config file not found";
    public static final String CONFIG_FILE_ENCODING_ERROR_MESSAGE =
            "Invalid config file encoding";

    public List<TreespaceConfig> treespaces =
            new ArrayList<TreespaceConfig>();

    public Map<String, String> auth_config;

    public int rest_service_port = 0;

    public RootConfig() {
    }

    public void validate() throws ConfigException {
        // Check that all of the treespace configurations are valid.
        if (treespaces != null) {
            for (TreespaceConfig treespaceConfig: treespaces) {
                treespaceConfig.validate();
            }
        }
    }

    public void merge(RootConfig config) {
        if (config.treespaces != null) {
            HashMap<String, TreespaceConfig> tmap = new HashMap<>();
            for (TreespaceConfig t : treespaces) {
                if (t.name != null)
                    tmap.put(t.name, t);
            }
            for (TreespaceConfig t : config.treespaces) {
                if (t.name != null && tmap.containsKey(t.name))
                    tmap.get(t.name).merge(t);
                else
                    treespaces.add(t);
            }
        }
        if (config.auth_config != null) {
            if (auth_config == null)
                auth_config = new HashMap<>();
            auth_config.putAll(config.auth_config);
        }
        if (config.rest_service_port > 0) {
            this.rest_service_port = config.rest_service_port;
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        RootConfig other = (RootConfig) obj;
        if (!treespaces.equals(other.treespaces))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;
        hashCode = prime * hashCode + treespaces.hashCode();
        return hashCode;
    }

    /**
     * Load the root configuration from an input stream. Usually called
     * indirectly via loadConfigStream or loadConfigString.
     * Throws a ConfigException if the data in the input stream is not
     * valid YAML or if the format doesn't match this RootConfig class
     * (or the other *Config classes) or it doesn't pass the validation
     * routines of the *Config classes.
     * @param inputStream
     * @return
     * @throws ConfigException
     */
    public static RootConfig loadConfigStream(InputStream inputStream)
            throws ConfigException {
        Yaml yaml = new Yaml();
        RootConfig config = null;
        try {
            config = yaml.loadAs(inputStream, RootConfig.class);
        }
        catch (Exception exc) {
            throw new ConfigException(LOAD_DESERIALIZE_ERROR_MESSAGE, exc);
        }
        if (config != null)
            config.validate();
        return config;
    }

    public static RootConfig loadConfigFile(File file)
            throws ConfigException {
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            return loadConfigStream(fileInputStream);
        }
        catch (FileNotFoundException exc) {
            throw new ConfigException(CONFIG_FILE_NOT_FOUND_ERROR_MESSAGE, exc);
        }
    }

    public static RootConfig loadConfigFile(String path)
            throws ConfigException {
        return loadConfigFile(new File(path));
    }

    public static RootConfig loadConfigResource(String name)
            throws ConfigException {
        InputStream inputStream =
                RootConfig.class.getResourceAsStream(name);
        if (inputStream == null)
            throw new ConfigException("Resource not found: " + name);
        return loadConfigStream(inputStream);
    }

    public static RootConfig loadConfigString(String text)
            throws ConfigException {
        try {
            byte[] bytes = text.getBytes("UTF-8");
            InputStream inputStream = new ByteArrayInputStream(bytes);
            return loadConfigStream(inputStream);
        }
        catch (UnsupportedEncodingException exc) {
            throw new ConfigException(CONFIG_FILE_ENCODING_ERROR_MESSAGE, exc);
        }
    }

    public AuthConfig getAuthConfig() {
        if(this.auth_config == null || this.auth_config.isEmpty())
            return AuthConfig.getDefault();
        AuthConfig res = new AuthConfig();
        for(Entry<String, String> e : this.auth_config.entrySet()) {
            res.setParam(e.getKey(), e.getValue());
        }
        return res;
    }

}
