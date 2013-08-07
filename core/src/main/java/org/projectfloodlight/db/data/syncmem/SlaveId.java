package org.projectfloodlight.db.data.syncmem;

public class SlaveId {
    private final String id;

    public SlaveId(String id) {
        assert id != null && ! id.isEmpty();
        this.id = id;
    }

    @Override
    public String toString() {
        return "SlaveId [id=" + id + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
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
        SlaveId other = (SlaveId) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }
}
