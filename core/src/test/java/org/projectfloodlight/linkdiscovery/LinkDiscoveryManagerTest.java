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

package org.projectfloodlight.linkdiscovery;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortFeatures;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.util.HexString;
import org.projectfloodlight.core.FloodlightContext;
import org.projectfloodlight.core.IFloodlightProviderService;
import org.projectfloodlight.core.IOFSwitch;
import org.projectfloodlight.core.ImmutablePort;
import org.projectfloodlight.core.IListener.Command;
import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.core.test.MockThreadPoolService;
import org.projectfloodlight.db.IBigDBService;
import org.projectfloodlight.db.MockBigDBService;
import org.projectfloodlight.linkdiscovery.ILinkDiscoveryListener;
import org.projectfloodlight.linkdiscovery.ILinkDiscoveryService;
import org.projectfloodlight.linkdiscovery.LinkDiscoveryManager;
import org.projectfloodlight.linkdiscovery.LinkInfo;
import org.projectfloodlight.packet.Data;
import org.projectfloodlight.packet.Ethernet;
import org.projectfloodlight.packet.IPacket;
import org.projectfloodlight.packet.IPv4;
import org.projectfloodlight.packet.UDP;
import org.projectfloodlight.routing.IRoutingService;
import org.projectfloodlight.routing.Link;
import org.projectfloodlight.sync.IStoreClient;
import org.projectfloodlight.sync.IStoreListener;
import org.projectfloodlight.sync.ISyncService;
import org.projectfloodlight.sync.Versioned;
import org.projectfloodlight.sync.test.MockSyncService;
import org.projectfloodlight.test.FloodlightTestCase;
import org.projectfloodlight.threadpool.IThreadPoolService;
import org.projectfloodlight.topology.ITopologyService;
import org.projectfloodlight.topology.NodePortTuple;
import org.projectfloodlight.topology.TopologyManager;
import org.projectfloodlight.tunnel.ITunnelManagerListener;
import org.projectfloodlight.tunnel.ITunnelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class LinkDiscoveryManagerTest extends FloodlightTestCase {

    private TestLinkDiscoveryManager ldm;
    protected static Logger log = LoggerFactory.getLogger(LinkDiscoveryManagerTest.class);
    ITunnelService tunnelManager;
    private IOFSwitch sw1, sw2;
    private ImmutablePort p1;
    private ImmutablePort p2;
    
    public class TestLinkDiscoveryManager extends LinkDiscoveryManager {
        public boolean isSendLLDPsCalled = false;

        @Override
        protected void discoverOnAllPorts() {
            isSendLLDPsCalled = true;
            super.discoverOnAllPorts();
        }

        public void reset() {
            isSendLLDPsCalled = false;
        }
    }

    public LinkDiscoveryManager getLinkDiscoveryManager() {
        return ldm;
    }

    private IOFSwitch createMockSwitch(Long id) {
        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(id).anyTimes();
//        InetAddress netaddr = new InetAddress("1.1.1." + id%255, (long) 5678);
//        expect(mockSwitch.getIInetAddress()).andReturn(netaddr).anyTimes();
        expect(mockSwitch.getInetAddress()).andReturn(null).anyTimes();
        expect(mockSwitch.getActions()).andReturn(0xffff).anyTimes();
        return mockSwitch;
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        FloodlightModuleContext cntx = new FloodlightModuleContext();
        ldm = new TestLinkDiscoveryManager();
        TopologyManager routingEngine = new TopologyManager();
        ldm.linkDiscoveryAware = new ArrayList<ILinkDiscoveryListener>();
        MockThreadPoolService tp = new MockThreadPoolService();
        MockBigDBService bigdb = new MockBigDBService();
        bigdb.addModuleSchema("floodlight", "2012-10-22");
        bigdb.addModuleSchema("topology");
        tunnelManager = createMock(ITunnelService.class);
        tunnelManager.addListener(anyObject(ITunnelManagerListener.class));
        expect(tunnelManager.getTunnelPortNumber(anyLong()))
            .andReturn(null).anyTimes();
        replay(tunnelManager);
        
        cntx.addService(ITunnelService.class, tunnelManager);
        cntx.addService(IThreadPoolService.class, tp);
        cntx.addService(IRoutingService.class, routingEngine);
        cntx.addService(ILinkDiscoveryService.class, ldm);
        cntx.addService(ITopologyService.class, ldm);
        cntx.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
        cntx.addService(IBigDBService.class, bigdb);
        cntx.addService(ISyncService.class, new MockSyncService());
        tp.init(cntx);
        routingEngine.init(cntx);
        ldm.init(cntx);
        bigdb.init(cntx);
        tp.startUp(cntx);
        routingEngine.startUp(cntx);
        ldm.startUp(cntx);
        bigdb.startUp(cntx);

        sw1 = createMockSwitch(1L);
        sw2 = createMockSwitch(2L);
        
        p1 = ImmutablePort.create("Eth1", (short)1);
        p2 = ImmutablePort.create("Eth2", (short)2);
        expect(sw1.getPort((short)1)).andReturn(p1).anyTimes();
        expect(sw2.getPort((short)1)).andReturn(p2).anyTimes();
        
        getMockFloodlightProvider().setSwitches(ImmutableMap.of(1L, sw1,
                                                                2L, sw2));
        replay(sw1, sw2);
    }

    @Test
    public void testAddOrUpdateLink() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(1L, 2, 2L, 1);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);


        NodePortTuple srcNpt = new NodePortTuple(1L, 2);
        NodePortTuple dstNpt = new NodePortTuple(2L, 1);

        // check invariants hold
        assertNotNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertTrue(linkDiscovery.switchLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(srcNpt));
        assertTrue(linkDiscovery.portLinks.get(srcNpt).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.portLinks.get(dstNpt).contains(lt));
        assertTrue(linkDiscovery.links.containsKey(lt));
    }

    @Test
    public void testDeleteLink() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(1L, 2, 2L, 1);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);
        linkDiscovery.deleteLinks(Collections.singletonList(lt), "Test");

        // check invariants hold
        assertNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertNull(linkDiscovery.switchLinks.get(lt.getDst()));
        assertNull(linkDiscovery.portLinks.get(lt.getSrc()));
        assertNull(linkDiscovery.portLinks.get(lt.getDst()));
        assertTrue(linkDiscovery.links.isEmpty());
    }

    @Test
    public void testAddOrUpdateLinkToSelf() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(1L, 2, 2L, 3);
        NodePortTuple srcNpt = new NodePortTuple(1L, 2);
        NodePortTuple dstNpt = new NodePortTuple(2L, 3);

        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);

        // check invariants hold
        assertNotNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertTrue(linkDiscovery.switchLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(srcNpt));
        assertTrue(linkDiscovery.portLinks.get(srcNpt).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.portLinks.get(dstNpt).contains(lt));
        assertTrue(linkDiscovery.links.containsKey(lt));
    }

    @Test
    public void testDeleteLinkToSelf() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(1L, 2, 1L, 3);
        NodePortTuple srcNpt = new NodePortTuple(1L, 2);
        NodePortTuple dstNpt = new NodePortTuple(2L, 3);

        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);
        linkDiscovery.deleteLinks(Collections.singletonList(lt), "Test to self");

        // check invariants hold
        assertNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertNull(linkDiscovery.switchLinks.get(lt.getDst()));
        assertNull(linkDiscovery.portLinks.get(srcNpt));
        assertNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.links.isEmpty());
    }

    @Test
    public void testRemovedSwitch() {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(1L, 2, 2L, 1);
        NodePortTuple srcNpt = new NodePortTuple(1L, 2);
        NodePortTuple dstNpt = new NodePortTuple(2L, 1);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);

        IOFSwitch sw1 = getMockFloodlightProvider().getSwitch(1L);
        IOFSwitch sw2 = getMockFloodlightProvider().getSwitch(2L);
        // Mock up our expected behavior
        linkDiscovery.switchRemoved(sw1.getId());
        verify(sw1, sw2);

        // check invariants hold
        assertNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertNull(linkDiscovery.switchLinks.get(lt.getDst()));
        assertNull(linkDiscovery.portLinks.get(srcNpt));
        assertNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.links.isEmpty());
    }

    @Test
    public void testRemovedSwitchSelf() {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        IOFSwitch sw1 = createMockSwitch(1L);
        replay(sw1);
        Link lt = new Link(1L, 2, 1L, 3);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);

        // Mock up our expected behavior
        linkDiscovery.switchRemoved(sw1.getId());

        verify(sw1);
        // check invariants hold
        assertNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertNull(linkDiscovery.portLinks.get(lt.getSrc()));
        assertNull(linkDiscovery.portLinks.get(lt.getDst()));
        assertTrue(linkDiscovery.links.isEmpty());
    }

    @Test
    public void testAddUpdateLinks() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();

        Link lt = new Link(1L, 1, 2L, 1);
        NodePortTuple srcNpt = new NodePortTuple(1L, 1);
        NodePortTuple dstNpt = new NodePortTuple(2L, 1);

        LinkInfo info;

        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            System.currentTimeMillis() - 40000, null);
        linkDiscovery.addOrUpdateLink(lt, info);

        // check invariants hold
        assertNotNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertTrue(linkDiscovery.switchLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(srcNpt));
        assertTrue(linkDiscovery.portLinks.get(srcNpt).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.portLinks.get(dstNpt).contains(lt));
        assertTrue(linkDiscovery.links.containsKey(lt));

        linkDiscovery.timeoutLinks();


        info = new LinkInfo(System.currentTimeMillis(),/* firstseen */
                            null,/* unicast */
                            System.currentTimeMillis());
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.links.get(lt).getUnicastValidTime() == null);
        assertTrue(linkDiscovery.links.get(lt).getMulticastValidTime() != null);


        // Add a link info based on info that woudld be obtained from unicast LLDP
        // Setting the unicast LLDP reception time to be 40 seconds old, so we can use
        // this to test timeout after this test.  Although the info is initialized
        // with LT_OPENFLOW_LINK, the link property should be changed to LT_NON_OPENFLOW
        // by the addOrUpdateLink method.
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            System.currentTimeMillis() - 40000, null);
        linkDiscovery.addOrUpdateLink(lt, info);

        // Expect to timeout the unicast Valid Time, but not the multicast Valid time
        // So the link type should go back to non-openflow link.
        linkDiscovery.timeoutLinks();
        assertTrue(linkDiscovery.links.get(lt).getUnicastValidTime() == null);
        assertTrue(linkDiscovery.links.get(lt).getMulticastValidTime() != null);

        // Set the multicastValidTime to be old and see if that also times out.
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000);
        linkDiscovery.addOrUpdateLink(lt, info);
        linkDiscovery.timeoutLinks();
        assertTrue(linkDiscovery.links.get(lt) == null);

        // Test again only with multicast LLDP
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000);
        linkDiscovery.addOrUpdateLink(lt, info);
        assertTrue(linkDiscovery.links.get(lt).getUnicastValidTime() == null);
        assertTrue(linkDiscovery.links.get(lt).getMulticastValidTime() != null);

        // Call timeout and check if link is no longer present.
        linkDiscovery.timeoutLinks();
        assertTrue(linkDiscovery.links.get(lt) == null);

        // Start clean and see if loops are also added.
        lt = new Link(1L, 1, 1L, 2);
        srcNpt = new NodePortTuple(1L, 1);
        dstNpt = new NodePortTuple(1L, 2);
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000);
        linkDiscovery.addOrUpdateLink(lt, info);


        // Start clean and see if loops are also added.
        lt = new Link(1L, 1, 1L, 3);
        srcNpt = new NodePortTuple(1L, 1);
        dstNpt = new NodePortTuple(1L, 3);
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000);
        linkDiscovery.addOrUpdateLink(lt, info);

        // Start clean and see if loops are also added.
        lt = new Link(1L, 4, 1L, 5);
        srcNpt = new NodePortTuple(1L, 4);
        dstNpt = new NodePortTuple(1L, 5);
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000);
        linkDiscovery.addOrUpdateLink(lt, info);

        // Start clean and see if loops are also added.
        lt = new Link(1L, 3, 1L, 5);
        srcNpt = new NodePortTuple(1L, 3);
        dstNpt = new NodePortTuple(1L, 5);
        info = new LinkInfo(System.currentTimeMillis() - 40000,
                            null, System.currentTimeMillis() - 40000);
        linkDiscovery.addOrUpdateLink(lt, info);
    }

    @Test
    public void testHARoleChange() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        replay(sw1, sw2);
        Link lt = new Link(1L, 2, 2L, 1);
        NodePortTuple srcNpt = new NodePortTuple(1L, 2);
        NodePortTuple dstNpt = new NodePortTuple(2L, 1);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        linkDiscovery.addOrUpdateLink(lt, info);

        // check invariants hold
        assertNotNull(linkDiscovery.switchLinks.get(lt.getSrc()));
        assertTrue(linkDiscovery.switchLinks.get(lt.getSrc()).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(srcNpt));
        assertTrue(linkDiscovery.portLinks.get(srcNpt).contains(lt));
        assertNotNull(linkDiscovery.portLinks.get(dstNpt));
        assertTrue(linkDiscovery.portLinks.get(dstNpt).contains(lt));
        assertTrue(linkDiscovery.links.containsKey(lt));

    }

    @Test
    public void testSwitchAdded() throws Exception {
        log.info("testSwitchAdded");

        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        Capture<OFMessage> wc;
        Capture<FloodlightContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort ofpp = new OFPhysicalPort();
        ofpp.setName("eth4242");
        ofpp.setPortNumber((short)4242);
        ofpp.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        ofpp.setCurrentFeatures(0);
        ImmutablePort p1 = ImmutablePort.fromOFPhysicalPort(ofpp);
        IOFSwitch sw1 = createMockSwitch(1L);

        // Set switch map in floodlightProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockFloodlightProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short p=1; p<=20; ++p) {
            ports.add(p);
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);
        fc = new Capture<FloodlightContext>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        sw1.write(capture(wc), capture(fc));
        expectLastCall().atLeastOnce();
        replay(sw1);

        linkDiscovery.switchActivated(sw1.getId());

        verify(sw1);

        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(100);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(200);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertTrue(qPorts.isEmpty());

        // Ensure that through every switch port, an LLDP and BDDP
        // packet was sent out.  Total # of packets = # of ports * 2.
        assertTrue("An open flow message should have been sent to the switch", wc.hasCaptured());
        List<OFMessage> msgList = wc.getValues();
        assertEquals(ports.size() * 2, msgList.size());
    }

    private OFPacketIn createPacketIn(String srcMAC, String dstMAC,
                                      String srcIp, String dstIp, short vlan) {
        IPacket testPacket = new Ethernet()
        .setDestinationMACAddress(dstMAC)
        .setSourceMACAddress(srcMAC)
        .setVlanID(vlan)
        .setEtherType(Ethernet.TYPE_IPv4)
        .setPayload(
                new IPv4()
                .setTtl((byte) 128)
                .setSourceAddress(srcIp)
                .setDestinationAddress(dstIp)
                .setPayload(new UDP()
                .setSourcePort((short) 5000)
                .setDestinationPort((short) 5001)
                .setPayload(new Data(new byte[] {0x01}))));
        byte[] testPacketSerialized = testPacket.serialize();
        OFPacketIn pi;
        // build out input packet
        pi = ((OFPacketIn) BasicFactory.getInstance().getMessage(OFType.PACKET_IN))
                .setBufferId(-1)
                .setInPort((short) 1)
                .setPacketData(testPacketSerialized)
                .setReason(OFPacketInReason.NO_MATCH)
                .setTotalLength((short) testPacketSerialized.length);
        return pi;
    }

    @Test
    public void testIgnoreSrcMAC() throws Exception {
        String mac1 = "00:11:22:33:44:55";
        String mac2 = "00:44:33:22:11:00";
        String mac3 = "00:44:33:22:11:02";
        String srcIp = "192.168.1.1";
        String dstIp = "192.168.1.2";
        short vlan = 42;

        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(1L).anyTimes();
        replay(mockSwitch);

        /* TEST1: See basic packet flow */
        OFPacketIn pi;
        pi = createPacketIn(mac1, mac2, srcIp, dstIp, vlan);
        FloodlightContext cntx = new FloodlightContext();
        Ethernet eth = new Ethernet();
        eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
        IFloodlightProviderService.bcStore.put(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                eth);
        Command ret;
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.CONTINUE, ret);

        /* TEST2: Add mac1 to the ignore MAC list and see that the packet is
         * dropped
         */
        ldm.addMACToIgnoreList(HexString.toLong(mac1), 0);
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.STOP, ret);
        /* Verify that if we send a packet with another MAC it still works */
        pi = createPacketIn(mac2, mac3, srcIp, dstIp, vlan);
        cntx = new FloodlightContext();
        eth = new Ethernet();
        eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
        IFloodlightProviderService.bcStore.put(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                eth);
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.CONTINUE, ret);

        /* TEST3: Add a MAC range and see if that is ignored */
        ldm.addMACToIgnoreList(HexString.toLong(mac2), 8);
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.STOP, ret);
        /* Send a packet with source MAC as mac3 and see that that is ignored
         * as well.
         */
        pi = createPacketIn(mac3, mac1, srcIp, dstIp, vlan);
        cntx = new FloodlightContext();
        eth = new Ethernet();
        eth.deserialize(pi.getPacketData(), 0, pi.getPacketData().length);
        IFloodlightProviderService.bcStore.put(cntx,
                IFloodlightProviderService.CONTEXT_PI_PAYLOAD,
                eth);
        ret = ldm.receive(mockSwitch, pi, cntx);
        assertEquals(Command.STOP, ret);

        verify(mockSwitch);
    }
    

    /**
     * In this case, autoportfast is disabled; and autoneg is ON.
     * @throws Exception
     */
    @Test
    public void testSwitchActivatedCase1() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        Capture<OFMessage> wc;
        Capture<FloodlightContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort p = new OFPhysicalPort();
        p.setName("Eth1");
        p.setPortNumber((short)1);
        p.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        p1 = ImmutablePort.fromOFPhysicalPort(p);
        IOFSwitch sw1 = createMock(IOFSwitch.class);

        // Set switch map in floodlightProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockFloodlightProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short i=1; i<=20; ++i) {
            ports.add(i);
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);
        fc = new Capture<FloodlightContext>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getActions()).andReturn(1).anyTimes();
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        expect(sw1.isFastPort(EasyMock.anyShort())).andReturn(false).anyTimes();
        sw1.write(capture(wc), capture(fc));
        expectLastCall().anyTimes();
        sw1.flush();
        expectLastCall().anyTimes();

        replay(sw1);

        // Set autoportfast feature to false
        linkDiscovery.setAutoPortFastFeature(false);
        // set the port autoneg feature to ON.
        p.setCurrentFeatures(OFPortFeatures.OFPPF_AUTONEG.getValue());
        p1 = ImmutablePort.fromOFPhysicalPort(p);

        linkDiscovery.switchActivated(sw1.getId());
        verify(sw1, tunnelManager);

        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(100);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(200);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertTrue(qPorts.isEmpty());

        // Ensure that through every switch port, an LLDP and BDDP
        // packet was sent out.  Total # of packets = # of ports * 2.
        assertTrue(wc.hasCaptured());
        List<OFMessage> msgList = wc.getValues();
        assertTrue(msgList.size() == ports.size() * 2);
    }

    /**
     * In this case, autoportfast is enabled; and autoneg is ON.
     * @throws Exception
     */
    @Test
    public void testSwitchActivatedCase2() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        Capture<OFMessage> wc;
        Capture<FloodlightContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort p = new OFPhysicalPort();
        p.setName("Eth1");
        p.setPortNumber((short)1);
        p.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        p1 = ImmutablePort.fromOFPhysicalPort(p);
        IOFSwitch sw1 = createMock(IOFSwitch.class);

        // Set switch map in floodlightProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockFloodlightProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short i=1; i<=20; ++i) {
            ports.add(i);
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);
        fc = new Capture<FloodlightContext>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getActions()).andReturn(1).anyTimes();
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        expect(sw1.isFastPort(EasyMock.anyShort())).andReturn(false).anyTimes();
        sw1.write(capture(wc), capture(fc));
        expectLastCall().anyTimes();
        sw1.flush();
        expectLastCall().anyTimes();

        replay(sw1);

        // Set autoportfast feature to true
        linkDiscovery.setAutoPortFastFeature(true);
        // set the port autoneg feature to ON.
        p.setCurrentFeatures(OFPortFeatures.OFPPF_AUTONEG.getValue());
        p1 = ImmutablePort.fromOFPhysicalPort(p);

        linkDiscovery.switchActivated(sw1.getId());
        verify(sw1, tunnelManager);

        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(100);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(200);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertTrue(qPorts.isEmpty());

        // Ensure that through every switch port, an LLDP and BDDP
        // packet was sent out.  Total # of packets = # of ports * 2.
        assertTrue(wc.hasCaptured());
        List<OFMessage> msgList = wc.getValues();
        assertTrue(msgList.size() == ports.size() * 2);
    }


    /**
     * In this case, autoportfast is disabled; and autoneg is OFF.
     * @throws Exception
     */
    @Test
    public void testSwitchActivatedCase3() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        Capture<OFMessage> wc;
        Capture<FloodlightContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort p = new OFPhysicalPort();
        p.setName("Eth1");
        p.setPortNumber((short)1);
        p.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        p1 = ImmutablePort.fromOFPhysicalPort(p);
        IOFSwitch sw1 = createMock(IOFSwitch.class);

        // Set switch map in floodlightProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockFloodlightProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short i=1; i<=10; ++i) {
            ports.add(i);
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);
        fc = new Capture<FloodlightContext>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getActions()).andReturn(1).anyTimes();
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        // Fast ports
        expect(sw1.isFastPort(EasyMock.anyShort())).andReturn(true).anyTimes();
        sw1.write(capture(wc), capture(fc));
        expectLastCall().anyTimes();
        sw1.flush();
        expectLastCall().anyTimes();

        replay(sw1);

        // Set autoportfast feature to false
        linkDiscovery.setAutoPortFastFeature(false);

        linkDiscovery.switchActivated(sw1.getId());
        verify(sw1, tunnelManager);

        // Since all ports are fast ports, none of them are quarantined.
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertTrue(qPorts.isEmpty());

        Thread.sleep(300);

        // Ensure that through every switch port, an LLDP and BDDP
        // packet was sent out.  Total # of packets = # of ports * 2.
        assertTrue(wc.hasCaptured());
        List<OFMessage> msgList = wc.getValues();
        assertTrue(msgList.size() == ports.size() * 2);
    }

    /**
     * In this case, autoportfast is disabled; and autoneg is OFF.
     * @throws Exception
     */
    @Test
    public void testSwitchActivatedCase4() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        Capture<OFMessage> wc;
        Capture<FloodlightContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort p = new OFPhysicalPort();
        p.setName("Eth1");
        p.setPortNumber((short)1);
        p.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        p1 = ImmutablePort.fromOFPhysicalPort(p);
        // set the port autoneg feature to OFF, thus fast port.
        IOFSwitch sw1 = createMock(IOFSwitch.class);

        // Set switch map in floodlightProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockFloodlightProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short i=1; i<=15; ++i) {
            ports.add(i);
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);
        fc = new Capture<FloodlightContext>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        // fast ports on
        expect(sw1.isFastPort(EasyMock.anyShort())).andReturn(true).anyTimes();
        sw1.write(capture(wc), capture(fc));
        expectLastCall().anyTimes();
        sw1.flush();
        expectLastCall().anyTimes();
        replay(sw1);

        // Set autoportfast feature to true
        linkDiscovery.setAutoPortFastFeature(true);

        linkDiscovery.switchActivated(sw1.getId());
        verify(sw1);

        // Since all ports are fast ports, none of them are quarantined.
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertTrue(qPorts.isEmpty());

        // Since autoportfast feature is on and all ports are fastports,
        // no LLDP or BDDP is sent out.
        assertFalse(wc.hasCaptured());
    }


    /**
     * Ensure that LLDPs are not sent through the tunnel ports.
     * @throws Exception
     */
    @Test
    public void testNoDiscoveryOnTunnelPorts() throws Exception {
        LinkDiscoveryManager linkDiscovery = getLinkDiscoveryManager();
        Capture<OFMessage> wc;
        Capture<FloodlightContext> fc;
        Set<Short> qPorts;
        OFPhysicalPort p = new OFPhysicalPort();
        p.setName("Eth1");
        p.setPortNumber((short)1);
        p.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        p1 = ImmutablePort.fromOFPhysicalPort(p);
        IOFSwitch sw1 = createMock(IOFSwitch.class);

        // Set switch map in floodlightProvider.
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        getMockFloodlightProvider().setSwitches(switches);

        // Create the set of ports
        List<Short> ports = new ArrayList<Short>();
        for(short i=1; i<=20; ++i) {
            ports.add(i);
        }

        // Set the captures.
        wc = new Capture<OFMessage>(CaptureType.ALL);
        fc = new Capture<FloodlightContext>(CaptureType.ALL);

        // Expect switch to return those ports.
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw1.getActions()).andReturn(1).anyTimes();
        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        expect(sw1.isFastPort(EasyMock.anyShort())).andReturn(false).anyTimes();
        sw1.write(capture(wc), capture(fc));
        expectLastCall().anyTimes();
        sw1.flush();
        expectLastCall().anyTimes();

        reset(tunnelManager);
        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong()))
            .andReturn((short)1).anyTimes();

        replay(sw1, tunnelManager);

        linkDiscovery.switchActivated(sw1.getId());
        verify(sw1);

        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(100);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertFalse(qPorts.isEmpty());

        Thread.sleep(200);
        qPorts = linkDiscovery.getQuarantinedPorts(sw1.getId());
        assertNotNull(qPorts);
        assertTrue(qPorts.isEmpty());

        // Ensure that through every switch port, an LLDP and BDDP
        // packet was sent out.  Total # of packets = # of ports * 2.
        assertTrue(wc.hasCaptured());
        List<OFMessage> msgList = wc.getValues();

        // Port #1 will not get LLDP as it is a tunnel port.
        assertTrue(msgList.size() == (ports.size() - 1) * 2);
    }

    @Test
    public void testKeysModified() throws Exception {
        LinkDiscoveryManager ld = getLinkDiscoveryManager();
        IStoreClient<Link, LinkInfo> storeClient = ld.getLinkStoreClient();

        Link lt = new Link(1L, 2, 2L, 1);
        LinkInfo info = new LinkInfo(System.currentTimeMillis(),
                                     System.currentTimeMillis(), null);
        Versioned<LinkInfo> v = new Versioned<LinkInfo>(info);

        Set<Link> linkSet = new HashSet<Link>();
        linkSet.add(lt);

        storeClient.put(lt, v);

        // Set expectations for tunnel manager
        reset(tunnelManager);
        expect(tunnelManager.getTunnelPortNumber(EasyMock.anyLong())).andReturn(null)
                                                                     .anyTimes();
        replay(tunnelManager);

        // call.
        ld.keysModified(linkSet.iterator(), IStoreListener.UpdateType.REMOTE);

        // verify
        verify(tunnelManager);

        // check invariants hold
        NodePortTuple srcNpt = new NodePortTuple(1L, 2);
        NodePortTuple dstNpt = new NodePortTuple(2L, 1);

        assertNotNull(ld.getSwitchLinks().get(lt.getSrc()));
        assertTrue(ld.getSwitchLinks().get(lt.getSrc()).contains(lt));
        assertNotNull(ld.getPortLinks().get(srcNpt));
        assertTrue(ld.getPortLinks().get(srcNpt).contains(lt));
        assertNotNull(ld.getPortLinks().get(dstNpt));
        assertTrue(ld.getPortLinks().get(dstNpt).contains(lt));
        assertTrue(ld.getLinks().containsKey(lt));

        // now delete the link and check if it works or not.

        storeClient.delete(lt);

        ld.keysModified(linkSet.iterator(), IStoreListener.UpdateType.REMOTE);

        // check invariants hold
        assertNull(ld.getSwitchLinks().get(lt.getSrc()));
        assertNull(ld.getSwitchLinks().get(lt.getDst()));
        assertNull(ld.getPortLinks().get(lt.getSrc()));
        assertNull(ld.getPortLinks().get(lt.getDst()));
        assertTrue(ld.getLinks().isEmpty());
    }
}
