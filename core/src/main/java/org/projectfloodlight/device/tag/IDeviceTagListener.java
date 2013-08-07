package org.projectfloodlight.device.tag;

import java.util.Iterator;

import org.projectfloodlight.device.IDevice;

public interface IDeviceTagListener {

    /**
     * Called when a new tag is added.
     * 
     * @param tag The tag that was added
     */
    public void tagAdded(DeviceTag tag);
    
    /**
     * Called when a tag is removed.
     * 
     * @param tag The tag that was removed
     */
    public void tagDeleted(DeviceTag tag);
    
    /**
     * Called when devices get re-mapped
     * @param devices
     */
    public void tagDevicesReMapped(Iterator<? extends IDevice> devices);
}
