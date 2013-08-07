package org.projectfloodlight.core.types;

import org.projectfloodlight.db.data.annotation.BigDBProperty;
import org.projectfloodlight.db.data.annotation.BigDBSerialize;
import org.projectfloodlight.db.data.serializers.LongToHexStringSerializer;

/**
 * Represents a switch interface as a tuple of Switch Id and the Interface name
 * @author munish_mehta
 *
 */
public class SwitchInterfaceTuple  {
    Long dpid; 
    String ifaceName;
    
    public SwitchInterfaceTuple(Long dpid, String ifaceName) {
        this.dpid = dpid;
        this.ifaceName = ifaceName;
    }

    @BigDBProperty("switch")
    @BigDBSerialize(using = LongToHexStringSerializer.class)
    public Long getDpid() {
        return dpid;
    }
    
    @BigDBProperty("interface")
    public String getIfaceName() {
        return ifaceName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dpid == null) ? 0 : dpid.hashCode());
        result = prime * result
                + ((ifaceName == null) ? 0 : ifaceName.hashCode());
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
        SwitchInterfaceTuple other = (SwitchInterfaceTuple) obj;
        if (dpid == null) {
            if (other.dpid != null)
                return false;
        } else if (!dpid.equals(other.dpid))
            return false;
        if (ifaceName == null) {
            if (other.ifaceName != null)
                return false;
        } else if (!ifaceName.equals(other.ifaceName))
            return false;
        return true;
    }
}
