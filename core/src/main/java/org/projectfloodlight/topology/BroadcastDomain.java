package org.projectfloodlight.topology;

import java.util.HashSet;
import java.util.Set;

import org.projectfloodlight.db.data.annotation.BigDBProperty;

/**
 * 
 * @author Srinivasan Ramasubramanian, Big Switch Networks
 *
 */
public class BroadcastDomain {
    private long id;
    private Set<NodePortTuple> ports;

    public BroadcastDomain() {
        id = 0;
        ports = new HashSet<NodePortTuple>();
    }

    @Override 
    public int hashCode() {
        return (int)(id ^ id>>>32);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        BroadcastDomain other = (BroadcastDomain) obj;

        return this.ports.equals(other.ports);
    }

    @BigDBProperty("id")
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @BigDBProperty("node")
    public Set<NodePortTuple> getPorts() {
        return ports;
    }

    public void add(NodePortTuple npt) {
        ports.add(npt);
    }

    @Override
    public String toString() {
        return "BroadcastDomain [id=" + id + ", ports=" + ports + "]";
    }
}
