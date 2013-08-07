package org.projectfloodlight.core.types;

import org.projectfloodlight.core.bigdb.serializers.UShortDataNodeSerializer;
import org.projectfloodlight.db.data.annotation.BigDBSerialize;

/**
 * An OpenFlow port number along with it's interface name.
 * @author alexreimers
 *
 */
public class PortInterfacePair {

    private String name;
    private short num;
    
    @BigDBSerialize(using=UShortDataNodeSerializer.class)
    public short getNumber() {
        return num;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * Constructor.
     * @param portNum The OpenFlow port number.
     * @param interfaceName The port name.
     */
    public PortInterfacePair(short portNum, String interfaceName) {
        num = portNum;
        name = interfaceName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + num;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        PortInterfacePair other = (PortInterfacePair) obj;
        if (name == null) {
            if (other.name != null) return false;
        } else if (!name.equals(other.name)) return false;
        if (num != other.num) return false;
        return true;
    }
}
