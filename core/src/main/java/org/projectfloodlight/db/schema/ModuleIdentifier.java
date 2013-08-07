package org.projectfloodlight.db.schema;

public final class ModuleIdentifier {

    private final String name;
    private final String revision;

    public ModuleIdentifier(String name) {
        this(name, null);
    }
    
    public ModuleIdentifier(String name, String revision) {
        assert name != null;
        this.name = name;
        this.revision = revision;
    }
    
    public String getName() {
        return name;
    }
    
    public String getRevision() {
        return revision;
    }
    
    public String toString() {
        String str = name;
        if ((revision != null) && !revision.isEmpty())
            str = str + "@" + revision;
        return str;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result =
                prime * result
                        + ((revision == null) ? 0 : revision.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        ModuleIdentifier other = (ModuleIdentifier) obj;
        if (name == null) {
            if (other.name != null) return false;
        } else if (!name.equals(other.name)) return false;
        if (revision == null) {
            if (other.revision != null) return false;
        } else if (!revision.equals(other.revision)) return false;
        return true;
    }
}
