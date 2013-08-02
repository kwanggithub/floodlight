package net.floodlightcontroller.device;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;



/**
 * This is the switch dpid, iface name tuple
 */
public class SwitchInterface {
    Long dpid;
    String ifaceName;

    /**
     * @param dpid2
     * @param ifaceName2
     */
    public SwitchInterface(Long dpid2, String ifaceName2) {
        this.dpid = dpid2;
        this.ifaceName = ifaceName2;
    }

    public SwitchInterface(SwitchInterface other) {
        this.dpid = other.dpid;
        this.ifaceName = other.ifaceName;
    }

    public Long getSwitchDPID() {
        return dpid;
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
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        SwitchInterface other = (SwitchInterface) obj;
        if (dpid == null) {
            if (other.dpid != null) return false;
        } else if (!dpid.equals(other.dpid)) return false;
        if (ifaceName == null) {
            if (other.ifaceName != null) return false;
        } else if (!ifaceName.equals(other.ifaceName)) return false;
        return true;
    }

    public Short getPortNumber(IFloodlightProviderService floodlightProvider) {
        IOFSwitch sw = floodlightProvider.getSwitch(dpid);
        if (sw == null)
            return null;
        ImmutablePort p = sw.getPort(ifaceName);
        if (p == null)
            return null;
        return p.getPortNumber();
    }
}
