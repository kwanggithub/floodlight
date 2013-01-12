package net.floodlightcontroller.virtualnetwork;

import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jackson.map.annotate.JsonSerialize;

import net.floodlightcontroller.util.MACAddress;

/**
 * Data structure for storing and outputing information of a virtual network created
 * by VirtualNetworkFilter
 * 
 * @author KC Wang
 */

@JsonSerialize(using=VirtualNetworkSerializer.class)
public class VirtualNetwork{
    protected String name; // network name
    protected String guid; // network id
    protected String gateway; // network gateway
    protected ConcurrentHashMap<MACAddress, VirtualNetworkHost> hosts; // array of hosts explicitly added to this network
    
    /**
     * Constructor requires network name and id
     * @param name: network name
     * @param guid: network id 
     */
    public VirtualNetwork(String name, String guid) {
        this.name = name;
        this.guid = guid;
        this.gateway = null;
        this.hosts = new ConcurrentHashMap<MACAddress, VirtualNetworkHost>();
        return;        
    }

    /**
     * Sets network name
     * @param gateway: IP address as String
     */
    public void setName(String name){
        this.name = name;
        return;                
    }
    
    /**
     * Sets network gateway IP address
     * @param gateway: IP address as String
     */
    public void setGateway(String gateway){
        this.gateway = gateway;
        return;                
    }
    
    /**
     * Adds a host to this network record
     * @param host: MAC address as MACAddress
     */
    public void addHost(VirtualNetworkHost host){
        removeHostByMAC(host.mac);
        this.hosts.put(host.mac, host);
        return;        
    }
    
    /**
     * Removes a host from this network record
     * @param host: MAC address as MACAddress
     * @return boolean: true: removed, false: host not found
     */
    public boolean removeHostByMAC(MACAddress hostMac){
        if (hosts.containsKey(hostMac)) {
            hosts.remove(hostMac);
            return true;
        }
        return false;
    }
    
    /**
     * Removes all hosts from this network record
     */
    public void clearHosts(){
        this.hosts.clear();
    }
}