package net.floodlightcontroller.device.tag;

import java.util.Set;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.device.IDevice;

public interface IDeviceTagService extends IFloodlightService {
    
    /**
     * Create a new tag.
     * @param tag
     */
    public DeviceTag createTag(String ns, String name, String value);
    
    public DeviceTag createTag(String ns, String name, String value, boolean persist);
    
    /**
     * Add a new tag.
     * @param tag
     */
    public void addTag(DeviceTag tag);

    /**
     * Delete a new tag.
     * @param tag
     */
    public void deleteTag(DeviceTag tag) throws TagDoesNotExistException;
    
    /**
     * Map a tag to a host.
     * @param tag
     * @param vlan TODO
     * @param dpid TODO
     * @param interfaceName TODO
     */
    public void mapTagToHost(DeviceTag tag, String hostmac, Short vlan, String dpid, 
                             String interfaceName)
        throws TagDoesNotExistException, 
                TagInvalidHostMacException;
    
    /**
     * Unmap a tag from a host.
     * @param tag
     * @param vlan TODO
     * @param dpid TODO
     * @param interfaceName TODO
     */
    public void unmapTagToHost(DeviceTag tag, String hostmac, Short vlan, String dpid,
                               String interfaceName)
        throws TagDoesNotExistException,
                TagInvalidHostMacException;
    
    /**
     * getter for tag mappings to a host.
     * @param hostmac
     * @param vlan
     * @param dpid
     * @param interfaceName
     * @return
     */
    public Set<DeviceTag> getTagsByHost(String hostmac, Short vlan, String dpid,
                               String interfaceName);
    
    /**
     * return ITags with the given namespace and name.
     * @param namespace
     * @param name
     * @return
     */
    public Set<DeviceTag> getTags(String ns, String name);
    
    
    /**
     * Get a set of all tags for the given namespace. 
     * 
     * The returned set is /not/ synchronized
     * @param namespace
     * @return
     */
    public Set<DeviceTag> getTagsByNamespace(String namespace);
    
    /** 
     * Add a listener for tag creation, deletion, host mapping and unmapping.
     * @param listener The listener instance to call
     */
    public void addListener(IDeviceTagListener listener);
    
    /** Remove a tag listener
     * @param listener The previously installed listener instance
     */
    public void removeListener(IDeviceTagListener listener);
    
    /**
     * return set of tags for a given device
     * The returned set is /not/ synchronized
     * @param device
     * @return
     */
    public Set<DeviceTag> getTagsByDevice(IDevice device);
    
    /**
     * Return set of devices given a tag.
     * @param tag
     * @return
     */
    public Set<IDevice> getDevicesByTag(String tag);
}
