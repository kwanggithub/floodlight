package org.projectfloodlight.core.types;

import org.openflow.util.HexString;
import org.projectfloodlight.db.data.annotation.BigDBProperty;
import org.projectfloodlight.topology.NodePortTuple;

/**
 * Represents a Switch DPID along with it's port and interface.
 * @author alexreimers
 *
 */
public class NodeInterfaceTuple {
    protected String nodeId; // switch DPID
    protected PortInterfacePair intf;
    
    public NodeInterfaceTuple(long nodeId, PortInterfacePair intf) {
        this.nodeId = HexString.toHexString(nodeId);
        this.intf = intf;
    }
    
    public NodeInterfaceTuple(NodePortTuple npt, String intfName) {
        this.nodeId = HexString.toHexString(npt.getNodeId());
        intf = new PortInterfacePair(npt.getPortId(), intfName);
    }
    
    @BigDBProperty("switch-dpid")
    public String getNodeId() {
        return nodeId;
    }
    
    @BigDBProperty("interface")
    public PortInterfacePair getPortInterfacePair() {
        return intf;
    }
}
