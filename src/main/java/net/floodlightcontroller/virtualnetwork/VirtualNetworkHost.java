package net.floodlightcontroller.virtualnetwork;

import java.util.ArrayList;

import net.floodlightcontroller.util.MACAddress;

public class VirtualNetworkHost {
    protected String port = null; // Logical port name
    protected String guid = null; // Network ID
    protected MACAddress mac = null; // MAC addresses
    protected ArrayList<Integer> ipAddresses;
    protected String hostId = null; // host name, attachment name
    protected String tenantId;
    
    public VirtualNetworkHost() {
        this.ipAddresses = new ArrayList<Integer>();
    }
    
    public void addIP(int ip) {
        if (!this.ipAddresses.contains(ip))
            this.ipAddresses.add(ip);
    }

    public void deleteIP(int ip) {
        this.ipAddresses.remove(ip);
    }
    
    public void setMAC(MACAddress addr) {
            this.mac = addr;
    }
    
    @SuppressWarnings("unchecked")
    public VirtualNetworkHost clone() {
        VirtualNetworkHost newHost = new VirtualNetworkHost();
        newHost.port = this.port;
        newHost.guid = this.guid;
        newHost.mac = this.mac;
        if (this.ipAddresses != null)
            newHost.ipAddresses = (ArrayList<Integer>) this.ipAddresses.clone();
        newHost.hostId = this.hostId;
        newHost.tenantId = this.tenantId;
        
        return newHost;
    }
}
