package org.projectfloodlight.device.tag;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.core.module.FloodlightModuleException;
import org.projectfloodlight.device.IDevice;
import org.projectfloodlight.device.IDeviceListener;
import org.projectfloodlight.device.IEntityClass;
import org.projectfloodlight.device.IEntityClassifierService;
import org.projectfloodlight.device.internal.AttachmentPoint;
import org.projectfloodlight.device.internal.Device;
import org.projectfloodlight.device.internal.Entity;
import org.projectfloodlight.device.internal.TaggingDeviceManagerImpl;
import org.projectfloodlight.device.test.MockDevice;
import org.projectfloodlight.sync.test.MockSyncService;

/**
 * Mock device manager useful for unit tests
 * @author readams
 */
public class MockTaggingDeviceManager extends TaggingDeviceManagerImpl {
    /**
     * Set a new IEntityClassifier
     * Use this as a quick way to use a particular entity classifier in a
     * single test without having to setup the full FloodlightModuleContext
     * again.
     * @param ecs
     */
    public void setEntityClassifier(IEntityClassifierService ecs) {
        this.entityClassifier = ecs;
        try {
            this.startUp(null);
        } catch (FloodlightModuleException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getSwitchInterfaceName(long dpid, short interfaceNumber) {
        return "test-interface-name";
    }
    
    /**
     * Learn a device using the given characteristics.
     * @param macAddress the MAC
     * @param vlan the VLAN (can be null)
     * @param ipv4Address the IP (can be null)
     * @param switchDPID the attachment point switch DPID (can be null)
     * @param switchPort the attachment point switch port (can be null)
     * @param processUpdates if false, will not send updates.  Note that this
     * method is not thread safe if this is false
     * @return the device, either new or not
     */
    public IDevice learnEntity(long macAddress, Short vlan,
                               Integer ipv4Address, Long switchDPID,
                               Integer switchPort,
                               boolean processUpdates) {
        List<IDeviceListener> listeners = deviceListeners.getOrderedListeners();
        if (!processUpdates) {
            deviceListeners.clearListeners();
        }

        if (vlan != null && vlan.shortValue() <= 0)
            vlan = null;
        if (ipv4Address != null && ipv4Address == 0)
            ipv4Address = null;
        IDevice res =  learnDeviceByEntity(new Entity(macAddress, vlan,
                                                      ipv4Address, switchDPID,
                                                      switchPort, new Date()));
        // Restore listeners
        if (listeners != null) {
            for (IDeviceListener listener : listeners) {
                deviceListeners.addListener("device", listener);
            }
        }
        return res;
    }

    /**
     * Learn a device using the given characteristics.
     * @param macAddress the MAC
     * @param vlan the VLAN (can be null)
     * @param ipv4Address the IP (can be null)
     * @param switchDPID the attachment point switch DPID (can be null)
     * @param switchPort the attachment point switch port (can be null)
     * @return the device, either new or not
     */
    public IDevice learnEntity(long macAddress, Short vlan,
                               Integer ipv4Address, Long switchDPID,
                               Integer switchPort) {
        return learnEntity(macAddress, vlan, ipv4Address,
                           switchDPID, switchPort, true);
    }

    @Override
    protected Device allocateDevice(Long deviceKey,
                                    Entity entity,
                                    IEntityClass entityClass) {
        return new MockDevice(this, deviceKey, entity, entityClass);
    }

    @Override
    protected Device allocateDevice(Long deviceKey,
                                    String dhcpClientName,
                                    List<AttachmentPoint> oldAPs,
                                    List<AttachmentPoint> attachmentPoints,
                                    Collection<Entity> entities,
                                    IEntityClass entityClass) {
        return new MockDevice(this, deviceKey, oldAPs,
                              attachmentPoints, entities, entityClass);
    }

    @Override
    protected Device allocateDevice(Device device,
                                    Entity entity,
                                    int insertionpoint) {
        return new MockDevice(device, entity, insertionpoint);
    }

    @Override
    public void init(FloodlightModuleContext fmc) throws FloodlightModuleException {
        super.init(fmc);
        setSyncServiceIfNotSet(new MockSyncService());
    }
}
