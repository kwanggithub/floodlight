package org.projectfloodlight.device.test;


import java.util.Collection;
import java.util.List;

import org.projectfloodlight.device.IEntityClass;
import org.projectfloodlight.device.IEntityClassifierService;
import org.projectfloodlight.device.internal.AttachmentPoint;
import org.projectfloodlight.device.internal.DefaultEntityClassifier;
import org.projectfloodlight.device.internal.Device;
import org.projectfloodlight.device.internal.DeviceManagerImpl;
import org.projectfloodlight.device.internal.Entity;

public class BigDBRestAPIMockDeviceManager extends DeviceManagerImpl {
    
    public BigDBRestAPIMockDeviceManager() {
        this.entityClassifier = new DefaultEntityClassifier();
    }


    public void setEntityClassifier(IEntityClassifierService ecs) {
        this.entityClassifier = ecs;
        //this.startUp(null);
    }
    
    // This is to avoid complicated setup for learning entities.
    @Override
    public Device learnDeviceByEntity(Entity entity) {
        Device d = this.findDeviceByEntity(entity);
        if (d == null) {
            long deviceKey = 0;
            synchronized (deviceKeyLock) {
                deviceKey = Long.valueOf(deviceKeyCounter++);
            } 
            Device d1 = allocateDevice(deviceKey, entity, 
                                       entityClassifier.classifyEntity(entity));
            if (d1 == null) {
                return null;
            }
            this.deviceMap.put(deviceKey, d1);
            this.updateIndices(d1, deviceKey);
            this.updateSecondaryIndices(entity, 
                                        entityClassifier.classifyEntity(entity), 
                                        deviceKey);
            return d1;
        } else {
            Device d2 = allocateDevice(d, entity, -1);
            deviceMap.replace(d.getDeviceKey(), d, d2);
            return d2;
        }
    }
    
    @Override
    public Device allocateDevice(Long deviceKey,
                                    Entity entity, 
                                    IEntityClass entityClass) {
        if (entityClass == null)
            return null;
        return new MockDevice(this, deviceKey, entity, entityClass);
    }
    
    @Override
    protected Device allocateDevice(Long deviceKey,
            String dhcpClientName,
            List<AttachmentPoint> aps,
            List<AttachmentPoint> trueAPs,
            Collection<Entity> entities,
            IEntityClass entityClass) {
        return new MockDevice(this, deviceKey, aps, trueAPs, entities, entityClass);
    }
    
    @Override
    public Device allocateDevice(Device device, Entity entity,
            int insertionPoint) {
        return new MockDevice(device, entity, insertionPoint);
    }
}
