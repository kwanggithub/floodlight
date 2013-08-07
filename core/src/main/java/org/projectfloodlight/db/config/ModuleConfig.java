package org.projectfloodlight.db.config;

public class ModuleConfig {
    
    public String name;
    public String revision;
    public String directory;
    public boolean recursive = false;
    
    public ModuleConfig() {
    }
    
    public void validate() throws ConfigException {
        // Check that a valid name and/or directory has been specified.
        // We don't check here that we can find the specified module.
        // We'll check that later when we actually try to load the module.
        boolean validName = (name != null) && !name.isEmpty();
        boolean validDirectory = (directory != null) && !directory.isEmpty();
        if (!validName && !validDirectory) {
            throw new ConfigException("Module config must contain either " +
                    "a valid module name or directory");
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((directory == null) ? 0 : directory.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + (recursive ? 1231 : 1237);
        result = prime * result
                + ((revision == null) ? 0 : revision.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        ModuleConfig other = (ModuleConfig) obj;
        if (directory == null) {
            if (other.directory != null) return false;
        } else if (!directory.equals(other.directory)) return false;
        if (name == null) {
            if (other.name != null) return false;
        } else if (!name.equals(other.name)) return false;
        if (recursive != other.recursive) return false;
        if (revision == null) {
            if (other.revision != null) return false;
        } else if (!revision.equals(other.revision)) return false;
        return true;
    }
}
