package net.bigdb.config;

public class ModuleSearchPathConfig {
    
    public String path;
    public boolean recursive = false;
    
    public ModuleSearchPathConfig() {
    }
    
    public void validate() throws ConfigException {
        // Check that a path has been specified.
        // We don't check here that it's actually a valid path.
        // We'll check that later when we actually try to load the schema data.
        if ((path == null) || path.isEmpty())
            throw new ConfigException("Invalid module location path");
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        result = prime * result + (recursive ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        ModuleSearchPathConfig other = (ModuleSearchPathConfig) obj;
        if (path == null) {
            if (other.path != null) return false;
        } else if (!path.equals(other.path)) return false;
        if (recursive != other.recursive) return false;
        return true;
    }
}
