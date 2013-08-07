/**
*    Copyright 2011, Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
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

package org.projectfloodlight.test;

import org.junit.Before;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFType;
import org.projectfloodlight.core.FloodlightContext;
import org.projectfloodlight.core.IFloodlightProviderService;
import org.projectfloodlight.core.test.MockFloodlightProvider;
import org.projectfloodlight.device.IDevice;
import org.projectfloodlight.device.IDeviceService;
import org.projectfloodlight.packet.Ethernet;

/**
 * This class gets a handle on the application context which is used to
 * retrieve Spring beans from during tests
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class FloodlightTestCase {
    protected MockFloodlightProvider mockFloodlightProvider;

    public MockFloodlightProvider getMockFloodlightProvider() {
        return mockFloodlightProvider;
    }

    public void setMockFloodlightProvider(MockFloodlightProvider mockFloodlightProvider) {
        this.mockFloodlightProvider = mockFloodlightProvider;
    }

    public FloodlightContext parseAndAnnotate(OFMessage m,
                                              IDevice srcDevice,
                                              IDevice dstDevice) {
        FloodlightContext bc = new FloodlightContext();
        return parseAndAnnotate(bc, m, srcDevice, dstDevice);
    }

    public FloodlightContext parseAndAnnotate(OFMessage m) {
        return parseAndAnnotate(m, null, null);
    }

    public static FloodlightContext parseAndAnnotate(FloodlightContext bc,
                                              OFMessage m,
                                              IDevice srcDevice,
                                              IDevice dstDevice) {
        if (OFType.PACKET_IN.equals(m.getType())) {
            OFPacketIn pi = (OFPacketIn)m;
            Ethernet eth = new Ethernet();
            eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
            IFloodlightProviderService.bcStore.put(bc,
                    IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                    eth);
        }
        if (srcDevice != null) {
            IDeviceService.fcStore.put(bc,
                    IDeviceService.CONTEXT_SRC_DEVICE,
                    srcDevice);
        }
        if (dstDevice != null) {
            IDeviceService.fcStore.put(bc,
                    IDeviceService.CONTEXT_DST_DEVICE,
                    dstDevice);
        }
        return bc;
    }

    @Before
    public void setUp() throws Exception {
        mockFloodlightProvider = new MockFloodlightProvider();
    }

    public static OFPhysicalPort createOFPhysicalPort(String name, int number) {
        OFPhysicalPort p = new OFPhysicalPort();
        p.setHardwareAddress(new byte [] { 0, 0, 0, 0, 0, 0 });
        p.setPortNumber((short)number);
        p.setName(name);
        return p;
    }
}
