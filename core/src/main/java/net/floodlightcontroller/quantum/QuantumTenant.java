package net.floodlightcontroller.quantum;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=QuantumTenantSerializer.class)
@JsonDeserialize(using=QuantumTenantDeserializer.class)
public class QuantumTenant {
    private String name;
    private Set<QuantumNetwork> networks;
    
    public QuantumTenant() {
        networks = new HashSet<QuantumNetwork>();
    }
    
    public QuantumTenant(String tenant) {
        this();
        this.name = tenant;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public Set<QuantumNetwork> getNetworks() {
        return networks;
    }
    
    public void addNetwork(QuantumNetwork qn) {
        networks.add(qn);
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        QuantumTenant other = (QuantumTenant) obj;
        if (name == null) {
            if (other.name != null) return false;
        } else if (!name.equals(other.name)) return false;
        return true;
    }
}
