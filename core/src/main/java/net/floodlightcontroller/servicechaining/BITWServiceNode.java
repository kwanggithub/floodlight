package net.floodlightcontroller.servicechaining;

import net.floodlightcontroller.topology.NodePortTuple;

public class BITWServiceNode extends ServiceNode {
    protected NodePortTuple ingressPort;
    protected NodePortTuple egressPort;

    public BITWServiceNode(String tenant, String name) {
        super(tenant, name, InsertionType.BUMPINTHEWIRE);
    }

    public NodePortTuple getIngressPort() {
        return ingressPort;
    }

    public void setIngressPort(NodePortTuple ingressPort) {
        this.ingressPort = ingressPort;
    }

    public NodePortTuple getEgressPort() {
        return egressPort;
    }

    public void setEgressPort(NodePortTuple egressPort) {
        this.egressPort = egressPort;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result
                 + ((egressPort == null) ? 0 : egressPort.hashCode());
        result = prime * result
                 + ((ingressPort == null) ? 0 : ingressPort.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        BITWServiceNode other = (BITWServiceNode) obj;
        if (egressPort == null) {
            if (other.egressPort != null) return false;
        } else if (!egressPort.equals(other.egressPort)) return false;
        if (ingressPort == null) {
            if (other.ingressPort != null) return false;
        } else if (!ingressPort.equals(other.ingressPort)) return false;
        return true;
    }
}
