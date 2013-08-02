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

package net.floodlightcontroller.topology;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import net.floodlightcontroller.bigdb.IBigDBService;
import net.floodlightcontroller.bigdb.MockBigDBService;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkDiscoveryManager;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.UpdateOperation;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.TopologyManager;
import net.floodlightcontroller.topology.TunnelEvent.TunnelLinkStatus;
import net.floodlightcontroller.tunnel.ITunnelService;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.test.MockSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyManagerTest extends FloodlightTestCase {
    protected static Logger log = LoggerFactory.getLogger(TopologyManagerTest.class);
    
    protected FloodlightModuleContext fmc;
    protected LinkDiscoveryManager ldm;
    protected ILinkDiscoveryService linkDiscoveryService;
    protected ITunnelService tunnelService;
    protected TopologyManager tm;

    private IOFSwitch createMockSwitch(Long id) {
        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        expect(mockSwitch.getId()).andReturn(id).anyTimes();
        return mockSwitch;
    }
    
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        fmc = new FloodlightModuleContext();
        MockThreadPoolService tp = new MockThreadPoolService();
        ldm = new LinkDiscoveryManager();
        tunnelService = createMock(ITunnelService.class);
        ISyncService syncManager = new MockSyncService();
        MockBigDBService bigdb = new MockBigDBService();
        bigdb.addModuleSchema("floodlight", "2012-10-22");
        bigdb.addModuleSchema("topology");

        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
        fmc.addService(ILinkDiscoveryService.class, ldm);
        fmc.addService(ITunnelService.class, tunnelService);
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(ISyncService.class, syncManager);        
        fmc.addService(IBigDBService.class, bigdb);

        tm  = new TopologyManager();

        // Set the timers shorter for faster completion of unit tests.
        tm.setTopologyComputeInterval(30);
        tm.setTunnelDetectionTimeout(90);
        tm.setTunnelVerificationTimeout(90);

        bigdb.init(fmc);
        ldm.init(fmc);
        tp.init(fmc);
        tm.init(fmc);
        ldm.startUp(fmc);
        tp.startUp(fmc);
        tm.startUp(fmc);
        
        // Create two physical ports.
        OFPhysicalPort ofpp = new OFPhysicalPort();
        ofpp.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:01"));
        ofpp.setPortNumber((short)1);
        ofpp.setName("port1");
        ofpp.setCurrentFeatures(0);
        ImmutablePort p1 = ImmutablePort.fromOFPhysicalPort(ofpp);

        ofpp = new OFPhysicalPort();
        ofpp.setHardwareAddress(HexString.fromHexString("5c:16:c7:00:00:02"));
        ofpp.setCurrentFeatures(0);
        ofpp.setPortNumber((short)2);
        ofpp.setName("port2");
        ImmutablePort p2 = ImmutablePort.fromOFPhysicalPort(ofpp);

        // Create mock switches.
        IOFSwitch sw1 = createMockSwitch(1L);
        IOFSwitch sw2 = createMockSwitch(2L);
        Map<Long, IOFSwitch> switches = new HashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        getMockFloodlightProvider().setSwitches(switches);

        Set<Short> ports = new HashSet<Short>();
        ports.add((short)1);

        expect(sw1.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw2.getEnabledPortNumbers()).andReturn(ports).anyTimes();
        expect(sw1.getPort(EasyMock.anyShort())).andReturn(p1).anyTimes();
        expect(sw2.getPort(EasyMock.anyShort())).andReturn(p2).anyTimes();
        replay(sw1, sw2);

        // Set tunnel manager expectations.  Switches 1 and 2 contain
        // one tunnel port, the port number is 1.
        expect(tunnelService.getTunnelPortNumber(EasyMock.anyLong())).andReturn(new Short((short)1)).anyTimes();
        expect(tunnelService.getTunnelIPAddr(2L)).andReturn(1).anyTimes();
        expect(tunnelService.isTunnelActiveByDpid(EasyMock.anyLong())).andReturn(true).anyTimes();
        replay(tunnelService);
    }

    @Test
    public void testBasic1() throws Exception {
        tm.addOrUpdateLink(1, (short)1, 2, (short)1, ILinkDiscovery.LinkType.DIRECT_LINK);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPortLinks().size()==2);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
        assertTrue(tm.getTunnelPorts().size()==0);

        tm.addOrUpdateLink(1, (short)2, 2, (short)2, ILinkDiscovery.LinkType.MULTIHOP_LINK);
        assertTrue(tm.getSwitchPorts().size() == 2);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==2);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==2);
        assertTrue(tm.getSwitchPortLinks().size()==4);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);
        assertTrue(tm.getTunnelPorts().size()==0);

        tm.removeLink(1, (short)2, 2, (short)2);
        assertTrue(tm.getSwitchPorts().get((long)1).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPorts().size() == 2);
        assertTrue(tm.getSwitchPortLinks().size()==2);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);

        tm.removeLink(1, (short)1, 2, (short)1);
        assertTrue(tm.getSwitchPorts().size() == 0);
        assertTrue(tm.getSwitchPortLinks().size()==0);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
    }

    @Test
    public void testBasic2() throws Exception {
        tm.addOrUpdateLink(1, (short)1, 2, (short)1, ILinkDiscovery.LinkType.DIRECT_LINK);
        tm.addOrUpdateLink(2, (short)2, 3, (short)1, ILinkDiscovery.LinkType.MULTIHOP_LINK);
        assertTrue(tm.getSwitchPorts().size() == 3);  // for two nodes.
        assertTrue(tm.getSwitchPorts().get((long)1).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==2);
        assertTrue(tm.getSwitchPorts().get((long)3).size()==1);
        assertTrue(tm.getSwitchPortLinks().size()==4);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);

        tm.removeLink(1, (short)1, 2, (short)1);
        assertTrue(tm.getSwitchPorts().size() == 2);
        assertTrue(tm.getSwitchPorts().get((long)1) == null);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)3).size()==1);
        assertTrue(tm.getSwitchPortLinks().size()==2);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);

        // nonexistent link // no null pointer exceptions.
        tm.removeLink(3, (short)1, 2, (short)2);
        assertTrue(tm.getSwitchPorts().size() == 2);
        assertTrue(tm.getSwitchPorts().get((long)1) == null);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)3).size()==1);
        assertTrue(tm.getSwitchPortLinks().size()==2);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);

        tm.removeLink(3, (short)2, 1, (short)2);
        assertTrue(tm.getSwitchPorts().size() == 2);
        assertTrue(tm.getSwitchPorts().get((long)1)==null);
        assertTrue(tm.getSwitchPorts().get((long)2).size()==1);
        assertTrue(tm.getSwitchPorts().get((long)3).size()==1);
        assertTrue(tm.getSwitchPortLinks().size()==2);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==2);

        tm.removeLink(2, (short)2, 3, (short)1);
        assertTrue(tm.getSwitchPorts().size() == 0);  // for two nodes.
        assertTrue(tm.getSwitchPortLinks().size()==0);
        assertTrue(tm.getPortBroadcastDomainLinks().size()==0);
        assertTrue(tm.getTunnelPorts().size()==0);
    }

    /**
     * This test ensures that when a tunnel event is removed properly from
     * the tunnel detection queue.
     * @throws Exception
     */
    @Test
    public void testDetectionCorrectness() throws Exception {

        BlockingQueue<OrderedNodePair> dqueue;
        tm.detectTunnelSource(1L, 2L);

        dqueue = tm.getTunnelDetectionQueue();

        assertTrue(dqueue.contains(new OrderedNodePair(1L, 2L)));
        tm.detectTunnelDestination(1L, 2L);
        assertTrue(dqueue.isEmpty());
    }

    /**
     * This test ensures that when a tunnel event is removed properly from
     * the tunnel verification queue.
     * @throws Exception
     */
    @Test
    public void testTunnelVerificationCorrectness() throws Exception {

        BlockingQueue<OrderedNodePair> dqueue, vqueue;
        tm.detectTunnelSource(1L, 2L);

        dqueue = tm.getTunnelDetectionQueue();
        vqueue = tm.getTunnelVerificationQueue();

        assertTrue(dqueue.contains(new OrderedNodePair(1L, 2L)));

        Thread.sleep(tm.TUNNEL_DETECTION_TIMEOUT_MS+ tm.getTopologyComputeInterval());
        assertTrue(dqueue.isEmpty());
        assertTrue(vqueue.contains(new OrderedNodePair(1L, 2L)));

        // This addOrUpdateTunnelLink is generated by LinkDiscoveryManager.
        LDUpdate update = new LDUpdate(1L, (short)1, 2L, (short)1,
                                       ILinkDiscovery.LinkType.TUNNEL,
                                       UpdateOperation.LINK_UPDATED);
        tm.linkDiscoveryUpdate(update);
        Thread.sleep(tm.getTopologyComputeInterval() + 200);
        assertTrue(vqueue.isEmpty());
    }


    /**
     * This test ensures that the entire sequence of tunnel liveness detection
     * goes through all the three queues.
     * @throws Exception
     */
    @Test
    public void testDetectFailureSequence() throws Exception {

        BlockingQueue<OrderedNodePair> dqueue, vqueue;
        List<TunnelEvent> statusList;
        tm.detectTunnelSource(1L, 2L);

        dqueue = tm.getTunnelDetectionQueue();
        vqueue = tm.getTunnelVerificationQueue();

        assertTrue(dqueue.contains(new OrderedNodePair(1L, 2L)));

        Thread.sleep(tm.TUNNEL_DETECTION_TIMEOUT_MS+ tm.getTopologyComputeInterval());
        assertTrue(dqueue.isEmpty());
        assertTrue(vqueue.contains(new OrderedNodePair(1L, 2L)));

        Thread.sleep(tm.TUNNEL_VERIFICATION_TIMEOUT_MS+tm.getTopologyComputeInterval()+500);
        statusList = tm.getTunnelLivenessState();
        assertTrue(vqueue.isEmpty());
        assertTrue(statusList.size() == 1);
        assertTrue(statusList.get(0).getSrcDPID() == 1);
        assertTrue(statusList.get(0).getDstDPID() == 2);
        assertTrue(statusList.get(0).getStatus() == TunnelLinkStatus.DOWN);
    }

    /**
     * This test checks if tunnel domains are formed when a switch gets
     * added in the cases when tunnel manager views the switch as tunnel
     * active, but has not yet sent the listener notification to topology
     * manager.
     * @throws Exception
     */
    @Test
    public void testTunnelFormationOnSwitchAddition() throws Exception {

        reset(tunnelService);
        expect(tunnelService.isTunnelActiveByDpid(1L)).andReturn(true).atLeastOnce();
        expect(tunnelService.getTunnelPortNumber(1L))
        .andReturn(new Short((short) 1)).atLeastOnce();
        expect(tunnelService.isTunnelActiveByDpid(2L)).andReturn(true).atLeastOnce();
        expect(tunnelService.getTunnelPortNumber(2L))
        .andReturn(new Short((short) 1)).atLeastOnce();
        replay(tunnelService);

        tm.clearCurrentTopology();
        tm.addOrUpdateSwitch(1L);
        tm.addOrUpdateSwitch(2L);
        tm.createNewInstance();

        verify(tunnelService);
        Set<NodePortTuple> nptSet = tm.getTunnelPorts();
        NodePortTuple npt11 = new NodePortTuple(1L, 1);
        NodePortTuple npt12 = new NodePortTuple(2L, 1);
        assertTrue(nptSet.contains(npt11));
        assertTrue(nptSet.contains(npt12));

        // check if the two switches are in the same L2 domain, due to the
        // tunnel domain.
        assertTrue(tm.inSameL2Domain(1L, 2L));
    }
}
