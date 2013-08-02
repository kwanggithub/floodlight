package net.floodlightcontroller.quantum;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=QuantumNetworkSerializer.class)
@JsonDeserialize(using=QuantumNetworkDeserializer.class)
public class QuantumNetwork {
    private String name;
    private String id;
    private String gateway;
    private String state;
    private Set<QuantumPort> ports;
    
    public QuantumNetwork() {
        ports = new HashSet<QuantumPort>();
    }
    
    public QuantumNetwork(String name, String id) {
        this();
        this.name = name;
        this.id = id;
    }
    
    public QuantumNetwork(String name, String id, String gateway) {
        this(name, id);
        this.gateway = gateway;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGateway() {
        return gateway;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }
    
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void addPort(QuantumPort p) {
        ports.add(p);
    }
    
    public Set<QuantumPort> getPorts() {
        return ports;
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
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        QuantumNetwork other = (QuantumNetwork) obj;
        if (id == null) {
            if (other.id != null) return false;
        } else if (!id.equals(other.id)) return false;
        return true;
    }
}
