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

package org.projectfloodlight.hub;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.capture;
import static org.junit.Assert.*;

import java.util.Arrays;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.core.FloodlightContext;
import org.projectfloodlight.core.IOFMessageListener;
import org.projectfloodlight.core.IOFSwitch;
import org.projectfloodlight.core.test.MockFloodlightProvider;
import org.projectfloodlight.hub.Hub;
import org.projectfloodlight.packet.Data;
import org.projectfloodlight.packet.Ethernet;
import org.projectfloodlight.packet.IPacket;
import org.projectfloodlight.packet.IPv4;
import org.projectfloodlight.packet.UDP;
import org.projectfloodlight.test.FloodlightTestCase;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class HubTest extends FloodlightTestCase {
    protected OFPacketIn packetIn;
    protected IPacket testPacket;
    protected byte[] testPacketSerialized;
    private   MockFloodlightProvider mockFloodlightProvider;
    private Hub hub;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();

        mockFloodlightProvider = getMockFloodlightProvider();
        hub = new Hub();
        mockFloodlightProvider.addOFMessageListener(OFType.PACKET_IN, hub);
        hub.setFloodlightProvider(mockFloodlightProvider);
        
        // Build our test packet
        this.testPacket = new Ethernet()
            .setDestinationMACAddress("00:11:22:33:44:55")
            .setSourceMACAddress("00:44:33:22:11:00")
            .setEtherType(Ethernet.TYPE_IPv4)
            .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress("192.168.1.1")
                .setDestinationAddress("192.168.1.2")
                .setPayload(new UDP()
                            .setSourcePort((short) 5000)
                            .setDestinationPort((short) 5001)
                            .setPayload(new Data(new byte[] {0x01}))));
        this.testPacketSerialized = testPacket.serialize();

        // Build the PacketIn
        this.packetIn = ((OFPacketIn) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_IN))
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(this.testPacketSerialized)
            .setReason(OFPacketInReason.NO_MATCH)
            .setTotalLength((short) this.testPacketSerialized.length);
    }

    @Test
    public void testFloodNoBufferId() throws Exception {
        // build our expected flooded packetOut
        OFPacketOut po = ((OFPacketOut) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT))
            .setActions(Arrays.asList(new OFAction[] {new OFActionOutput().setPort(OFPort.OFPP_FLOOD.getValue())}))
            .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
            .setBufferId(-1)
            .setInPort((short) 1)
            .setPacketData(this.testPacketSerialized);
        po.setLengthU(OFPacketOut.MINIMUM_LENGTH + po.getActionsLengthU()
                + this.testPacketSerialized.length);

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        
        Capture<OFMessage> wc1 = new Capture<OFMessage>(CaptureType.ALL);
        Capture<FloodlightContext> bc1 = new Capture<FloodlightContext>(CaptureType.ALL);
        
        mockSwitch.write(capture(wc1), capture(bc1));

        // Start recording the replay on the mocks
        replay(mockSwitch);
        // Get the listener and trigger the packet in
        IOFMessageListener listener = mockFloodlightProvider.getListeners().get(
                OFType.PACKET_IN).get(0);
        listener.receive(mockSwitch, this.packetIn,
                         parseAndAnnotate(this.packetIn));

        // Verify the replay matched our expectations
        verify(mockSwitch);
        
        assertTrue(wc1.hasCaptured());
        OFMessage m = wc1.getValue();
        assertEquals(po, m);
    }

    @Test
    public void testFloodBufferId() throws Exception {
        MockFloodlightProvider mockFloodlightProvider = getMockFloodlightProvider();
        this.packetIn.setBufferId(10);

        // build our expected flooded packetOut
        OFPacketOut po = ((OFPacketOut) mockFloodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT))
            .setActions(Arrays.asList(new OFAction[] {new OFActionOutput().setPort(OFPort.OFPP_FLOOD.getValue())}))
            .setActionsLength((short) OFActionOutput.MINIMUM_LENGTH)
            .setBufferId(10)
            .setInPort((short) 1);
        po.setLengthU(OFPacketOut.MINIMUM_LENGTH + po.getActionsLengthU());

        // Mock up our expected behavior
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        Capture<OFMessage> wc1 = new Capture<OFMessage>(CaptureType.ALL);
        Capture<FloodlightContext> bc1 = new Capture<FloodlightContext>(CaptureType.ALL);
        
        mockSwitch.write(capture(wc1), capture(bc1));

        // Start recording the replay on the mocks
        replay(mockSwitch);
        // Get the listener and trigger the packet in
        IOFMessageListener listener = mockFloodlightProvider.getListeners().get(
                OFType.PACKET_IN).get(0);
        listener.receive(mockSwitch, this.packetIn,
                         parseAndAnnotate(this.packetIn));

        // Verify the replay matched our expectations
        verify(mockSwitch);
        
        assertTrue(wc1.hasCaptured());
        OFMessage m = wc1.getValue();
        assertEquals(po, m);
    }
}
