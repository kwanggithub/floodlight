package org.openflow.protocol.statistics;

import org.projectfloodlight.core.types.PortInterfacePair;
import org.projectfloodlight.db.data.annotation.BigDBProperty;

/**
 * An OFQueueStatisticsReply with added information.
 * @author alexreimers
 *
 */
public class OFQueueWithInterfaceStatisticsReply extends OFQueueStatisticsReply {
    private PortInterfacePair intf;
    
    public OFQueueWithInterfaceStatisticsReply(OFQueueStatisticsReply ofqr, String intfName) {
        intf = new PortInterfacePair(ofqr.getPortNumber(), intfName);
        this.setPortNumber(ofqr.getPortNumber());
        this.setQueueId(ofqr.getQueueId());
        this.setTransmitBytes(ofqr.getTransmitBytes());
        this.setTransmitErrors(ofqr.getTransmitErrors());
        this.setTransmitPackets(ofqr.getTransmitPackets());
    }
    
    @BigDBProperty("interface")
    public PortInterfacePair getInterface() {
        return intf;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((intf == null) ? 0 : intf.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        OFQueueWithInterfaceStatisticsReply other = (OFQueueWithInterfaceStatisticsReply) obj;
        if (intf == null) {
            if (other.intf != null) return false;
        } else if (!intf.equals(other.intf)) return false;
        return true;
    }
}
