/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.device.test;

import static net.floodlightcontroller.device.IDeviceService.DeviceField.MAC;
import static net.floodlightcontroller.device.IDeviceService.DeviceField.PORT;
import static net.floodlightcontroller.device.IDeviceService.DeviceField.SWITCH;
import static net.floodlightcontroller.device.IDeviceService.DeviceField.VLAN;

import java.util.EnumSet;

import net.floodlightcontroller.device.IDeviceService;
import net.floodlightcontroller.device.IEntityClass;
import net.floodlightcontroller.device.IDeviceService.DeviceField;
import net.floodlightcontroller.device.internal.DefaultEntityClassifier;
import net.floodlightcontroller.device.internal.Entity;

/** A simple IEntityClassifier. Useful for tests that need IEntityClassifiers 
 * and IEntityClass'es with switch and/or port key fields 
 */
public class MockEntityClassifier extends DefaultEntityClassifier {
    public static class TestEntityClass implements IEntityClass {
        @Override
        public EnumSet<DeviceField> getKeyFields() {
            return EnumSet.of(MAC, VLAN, SWITCH, PORT);
        }

        @Override
        public String getName() {
            return "TestEntityClass";
        }
    }
    public static IEntityClass testEC = 
            new MockEntityClassifier.TestEntityClass();
    
    @Override
    public IEntityClass classifyEntity(Entity entity) {
        if (entity.getSwitchDPID() >= 10L) {
            return testEC;
        }
        return DefaultEntityClassifier.entityClass;
    }

    @Override
    public EnumSet<IDeviceService.DeviceField> getKeyFields() {
        return EnumSet.of(MAC, VLAN, SWITCH, PORT);
    }

}