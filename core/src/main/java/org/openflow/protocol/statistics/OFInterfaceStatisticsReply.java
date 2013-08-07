package org.openflow.protocol.statistics;

import org.projectfloodlight.core.types.PortInterfacePair;
import org.projectfloodlight.db.data.annotation.BigDBProperty;


/**
 * An OFPortStatisticsReply that also includes the interface name
 * as well as port number.
 * @author alexreimers
 *
 */
public class OFInterfaceStatisticsReply extends OFPortStatisticsReply {
    private PortInterfacePair intf;
    
    public OFInterfaceStatisticsReply(OFPortStatisticsReply ofpr, String ifName) {
        intf = new PortInterfacePair(ofpr.getPortNumber(), ifName);
        this.setPortNumber(ofpr.getPortNumber());
        this.setCollisions(ofpr.getCollisions());
        this.setReceiveBytes(ofpr.getReceiveBytes());
        this.setReceiveCRCErrors(ofpr.getReceiveCRCErrors());
        this.setReceiveDropped(ofpr.getReceiveDropped());
        this.setReceiveErrors(ofpr.getReceiveErrors());
        this.setReceiveFrameErrors(ofpr.getReceiveFrameErrors());
        this.setReceiveOverrunErrors(ofpr.getReceiveOverrunErrors());
        this.setReceivePackets(ofpr.getReceivePackets());
        this.setTransmitBytes(ofpr.getTransmitBytes());
        this.setTransmitDropped(ofpr.getTransmitDropped());
        this.setTransmitErrors(ofpr.getTransmitErrors());
        this.setTransmitPackets(ofpr.getTransmitPackets());
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
        OFInterfaceStatisticsReply other = (OFInterfaceStatisticsReply) obj;
        if (intf == null) {
            if (other.intf != null) return false;
        } else if (!intf.equals(other.intf)) return false;
        return true;
    }
}
