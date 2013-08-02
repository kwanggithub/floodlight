package net.floodlightcontroller.tunnel;



public interface ITunnelManagerListener {

    public void tunnelPortActive(long dpid, short tunnelPortNumber);

    public void tunnelPortInactive(long dpid, short tunnelPortNumber);
}
