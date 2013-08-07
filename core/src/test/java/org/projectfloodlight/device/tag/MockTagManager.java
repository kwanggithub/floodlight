/**
 * 
 */
package org.projectfloodlight.device.tag;

import java.util.Set;

import org.projectfloodlight.device.IDevice;
import org.projectfloodlight.device.tag.DeviceTag;
import org.projectfloodlight.device.tag.IDeviceTagListener;
import org.projectfloodlight.device.tag.IDeviceTagService;
import org.projectfloodlight.device.tag.TagDoesNotExistException;
import org.projectfloodlight.device.tag.TagInvalidHostMacException;


/**
 * @author sandeephebbani
 *
 */
public class MockTagManager implements IDeviceTagService {

    private Set<IDevice> devices;
    private Set<DeviceTag> tags;


    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#createTag(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public DeviceTag createTag(String ns, String name, String value) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#createTag(java.lang.String, java.lang.String, java.lang.String, boolean)
     */
    @Override
    public DeviceTag createTag(String ns, String name, String value,
                         boolean persist) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#addTag(com.bigswitch.floodlight.tagmanager.Tag)
     */
    @Override
    public void addTag(DeviceTag tag) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#deleteTag(com.bigswitch.floodlight.tagmanager.Tag)
     */
    @Override
    public void deleteTag(DeviceTag tag) throws TagDoesNotExistException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#mapTagToHost(com.bigswitch.floodlight.tagmanager.Tag, java.lang.String, java.lang.Short, java.lang.String, java.lang.String)
     */
    @Override
    public
            void
            mapTagToHost(DeviceTag tag, String hostmac, Short vlan, String dpid,
                         String interfaceName) throws TagDoesNotExistException,
                    TagInvalidHostMacException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#unmapTagToHost(com.bigswitch.floodlight.tagmanager.Tag, java.lang.String, java.lang.Short, java.lang.String, java.lang.String)
     */
    @Override
    public
            void
            unmapTagToHost(DeviceTag tag, String hostmac, Short vlan, String dpid,
                           String interfaceName) throws TagDoesNotExistException,
                    TagInvalidHostMacException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#getTagsByHost(java.lang.String, java.lang.Short, java.lang.String, java.lang.String)
     */
    @Override
    public Set<DeviceTag> getTagsByHost(String hostmac, Short vlan, String dpid,
                                  String interfaceName) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#getTags(java.lang.String, java.lang.String)
     */
    @Override
    public Set<DeviceTag> getTags(String ns, String name) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#getTagsByNamespace(java.lang.String)
     */
    @Override
    public Set<DeviceTag> getTagsByNamespace(String namespace) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#addListener(com.bigswitch.floodlight.tagmanager.ITagListener)
     */
    @Override
    public void addListener(IDeviceTagListener listener) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#removeListener(com.bigswitch.floodlight.tagmanager.ITagListener)
     */
    @Override
    public void removeListener(IDeviceTagListener listener) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#getTagsByDevice(org.projectfloodlight.devicemanager.IDevice)
     */
    @Override
    public Set<DeviceTag> getTagsByDevice(IDevice device) {
        return tags;
    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#getDevicesByTag(com.bigswitch.floodlight.tagmanager.Tag)
     */
    @Override
    public Set<IDevice> getDevicesByTag(String tag) {
        return devices;
    }

    /**
     * @param devices
     */
    public void setDevices(Set<IDevice> devices) {
        this.devices = devices;
        
    }

    /**
     * @param noTags
     */
    public void setTags(Set<DeviceTag> tags) {
        this.tags = tags;
        
    }

}
