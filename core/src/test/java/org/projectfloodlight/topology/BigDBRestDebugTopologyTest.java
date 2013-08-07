package org.projectfloodlight.topology;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.projectfloodlight.core.IOFSwitch;
import org.projectfloodlight.core.ImmutablePort;
import org.projectfloodlight.core.IFloodlightProviderService.Role;
import org.projectfloodlight.core.internal.MockOFSwitchImpl;
import org.projectfloodlight.db.data.ServerDataSource;
import org.projectfloodlight.db.rest.BigDBRestAPITestBase;
import org.projectfloodlight.db.util.Path;
import org.projectfloodlight.routing.Link;
import org.projectfloodlight.topology.BroadcastDomain;
import org.projectfloodlight.topology.Cluster;
import org.projectfloodlight.topology.ITopologyService;
import org.projectfloodlight.topology.NodePortTuple;
import org.projectfloodlight.topology.OrderedNodePair;
import org.projectfloodlight.topology.bigdb.TopologyResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigDBRestDebugTopologyTest extends BigDBRestAPITestBase {
    private static ITopologyService topology;
    protected static Logger log = LoggerFactory.getLogger(BigDBRestDebugTopologyTest.class);

    public static String TOPOLOGY_BASE_URL;
    public static String EXTERNAL_LINKS_URL;
    public static String DEBUG_ALLOWED_PORTS_UNICAST;
    public static String DEBUG_ALLOWED_PORTS_BROADCAST;
    public static String DEBUG_ALLOWED_PORTS_EXTERNAL;
    public static String DEBUG_HIGHER_TOPOLOGY_OPENFLOW;
    public static String DEBUG_HIGHER_TOPOLOGY_BROADCAST;
    public static String DEBUG_HIGHER_TOPOLOGY_NEIGHBORS;
    public static String DEBUG_HIGHER_TOPOLOGY_NEXT_HOP;
    public static String DEBUG_HIGHER_TOPOLOGY_LAYER_TWO_DOMAINS;


    @BeforeClass
    public static void testSetup() throws Exception {
        dbService = defaultService();
        dbService.addModuleSchema("topology");
        
        moduleContext = defaultModuleContext();

        topology = EasyMock.createMock(ITopologyService.class);
        moduleContext.addService(ITopologyService.class, topology);
        
        setupBaseClass();
        getMockFloodlightProvider().setRole(Role.MASTER, "");

        TOPOLOGY_BASE_URL = REST_SERVER + "/api/v1/data/controller/topology/";
        EXTERNAL_LINKS_URL = TOPOLOGY_BASE_URL + "external-broadcast-interface";
        DEBUG_ALLOWED_PORTS_UNICAST = TOPOLOGY_BASE_URL + "debug/allowed-port/unicast";
        DEBUG_ALLOWED_PORTS_BROADCAST = TOPOLOGY_BASE_URL + "debug/allowed-port/broadcast";
        DEBUG_ALLOWED_PORTS_EXTERNAL = TOPOLOGY_BASE_URL + "debug/allowed-port/external";
        DEBUG_HIGHER_TOPOLOGY_OPENFLOW = TOPOLOGY_BASE_URL + "debug/higher-topology/domain/openflow";
        DEBUG_HIGHER_TOPOLOGY_BROADCAST = TOPOLOGY_BASE_URL + "debug/higher-topology/domain/broadcast";
        DEBUG_HIGHER_TOPOLOGY_NEIGHBORS = TOPOLOGY_BASE_URL + "debug/higher-topology/neighbor";
        DEBUG_HIGHER_TOPOLOGY_NEXT_HOP = TOPOLOGY_BASE_URL + "debug/higher-topology/next-hop";
        DEBUG_HIGHER_TOPOLOGY_LAYER_TWO_DOMAINS = TOPOLOGY_BASE_URL + "debug/higher-topology/layer-two-domain";
        
        TopologyResource bigTopologyResource =
                new TopologyResource(topology, null, 
                                     getMockFloodlightProvider(), null);
        ServerDataSource controllerDataSource =
                dbService.getControllerDataSource();
        // FIXME: Should change the path to be /core/topology.
        controllerDataSource.registerDynamicDataHooksFromObject(
                new Path("/topology"), bigTopologyResource);
    }

    @Before
    public void resetTopology() {
        // Called before each individual test
        EasyMock.reset(topology);
    }

    protected static IOFSwitch makeSwitchMock(long id) {
        MockOFSwitchImpl sw = new MockOFSwitchImpl();
        sw.setId(id);
        sw.setBuffers(256);
        sw.setCapabilities(135);
        sw.setConnectedSince(new Date(1355189241655L));

        // Add ports
        Collection<ImmutablePort> portC = new ArrayList<ImmutablePort>();
        for (short i = 0; i <= 10; i++) {
            ByteBuffer bb = ByteBuffer.allocate(6);
            bb.putInt(i);
            bb.flip();
            ImmutablePort pp = (new ImmutablePort.Builder())
                    .setName("port" + i)
                    .setPortNumber(i)
                    .setHardwareAddress(bb.array())
                    .build();
            portC.add(pp);
        }
        sw.setPorts(portC);

        // Add attributes
        Map<Object, Object> attrMap = new HashMap<Object, Object>();
        attrMap.put(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD, true);
        attrMap.put(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE, true);
        OFDescriptionStatistics desc = new OFDescriptionStatistics();
        desc.setManufacturerDescription("Big Switch Networks, Inc");
        desc.setHardwareDescription("Indigo");
        desc.setSoftwareDescription("Version 2.0");
        desc.setDatapathDescription("None");
        desc.setSerialNumber("626967737769746368");
        attrMap.put(IOFSwitch.SWITCH_DESCRIPTION_DATA, desc);
        sw.setAttributes(attrMap);
        return sw;
    }

    private static void setupMockSwitches() {
        for (long i = 0; i <= 4; i++) {
            getMockFloodlightProvider().addSwitch(makeSwitchMock(i));
        }
    }

    private static void addBroadcastDomains() {
        Set<BroadcastDomain> bdSet = new HashSet<BroadcastDomain>();
        BroadcastDomain bd = new BroadcastDomain();
        bd.setId(1);
        NodePortTuple npt = new NodePortTuple(1, 1);
        bd.add(npt);
        npt = new NodePortTuple(2, 1);
        bd.add(npt);
        bdSet.add(bd);
        bd = new BroadcastDomain();
        bd.setId(3);
        npt = new NodePortTuple(3, 1);
        bd.add(npt);
        npt = new NodePortTuple(4, 1);
        bd.add(npt);
        bdSet.add(bd);
        EasyMock.expect(topology.getBroadcastDomains()).andReturn(bdSet).anyTimes();
    }

    private static void setupAllowedUnicastPorts() {
        Map<OrderedNodePair, Set<NodePortTuple>> uniPorts = new HashMap<OrderedNodePair, Set<NodePortTuple>>();
        OrderedNodePair onp1 = new OrderedNodePair(1, 2);
        Set<NodePortTuple> sNpt = new HashSet<NodePortTuple>();
        sNpt.add(new NodePortTuple(1, 2));
        sNpt.add(new NodePortTuple(1, 3));
        sNpt.add(new NodePortTuple(2, 2));
        uniPorts.put(onp1, sNpt);
        OrderedNodePair onp2 = new OrderedNodePair(3, 4);
        Set<NodePortTuple> sNpt2 = new HashSet<NodePortTuple>();
        sNpt2.add(new NodePortTuple(3, 1));
        sNpt2.add(new NodePortTuple(3, 2));
        sNpt2.add(new NodePortTuple(4, 1));
        uniPorts.put(onp2, sNpt2);
        EasyMock.expect(topology.getAllowedUnicastPorts()).andReturn(uniPorts).anyTimes();
    }

    private static void setupAllowedBroadcastPorts() {
        Map<OrderedNodePair, NodePortTuple> ports = new HashMap<OrderedNodePair, NodePortTuple>();
        OrderedNodePair onp1 = new OrderedNodePair(1, 2);
        NodePortTuple npt1 = new NodePortTuple(1, 3);
        ports.put(onp1, npt1);
        OrderedNodePair onp2 = new OrderedNodePair(3, 4);
        NodePortTuple npt2 = new NodePortTuple(1, 3);
        ports.put(onp2, npt2);
        EasyMock.expect(topology.getAllowedIncomingBroadcastPorts()).andReturn(ports).anyTimes();
    }

    private static void setupAllowedExternalPorts() {
        Map<NodePortTuple, Set<Long>> ePorts = new HashMap<NodePortTuple, Set<Long>>();
        NodePortTuple npt1 = new NodePortTuple(1, 1);
        Set<Long> lSet = new HashSet<Long>();
        lSet.add((long) 2);
        lSet.add((long) 3);
        ePorts.put(npt1, lSet);
        NodePortTuple npt2 = new NodePortTuple(2, 3);
        Set<Long> lSet2 = new HashSet<Long>();
        lSet.add((long) 4);
        lSet.add((long) 5);
        ePorts.put(npt2, lSet2);
        EasyMock.expect(topology.getAllowedPortToBroadcastDomains()).andReturn(ePorts).anyTimes();
    }

    private static void setMockOpenFlowDomain() {
        Map<Long, Object> ofMap = new HashMap<Long, Object>();
        Cluster c = new Cluster();
        c.addLink(new Link(1, 1, 2, 2));
        c.addLink(new Link(2, 3, 3, 1));
        ofMap.put((long)1, c);
        c = new Cluster();
        c.addLink(new Link(4, 1, 5, 1));
        c.addLink(new Link(6, 1, 5, 2));
        ofMap.put((long)4, c);
        EasyMock.expect(topology.getHigherTopologyNodes()).andReturn(ofMap);
    }

    private static void setMockBroadcastDomain() {
        // object is actually a broadcastdomain
        Map<Long, Object> bdMap = new HashMap<Long, Object>();
        BroadcastDomain bd = new BroadcastDomain();
        bd.setId(1);
        NodePortTuple npt = new NodePortTuple(1, 1);
        bd.add(npt);
        npt = new NodePortTuple(2, 1);
        bd.add(npt);
        bd = new BroadcastDomain();
        bd.setId(3);
        npt = new NodePortTuple(3, 1);
        bd.add(npt);
        npt = new NodePortTuple(4, 1);
        bd.add(npt);
        bdMap.put((long)1, bd);
        bd = new BroadcastDomain();
        bd.setId(2);
        npt = new NodePortTuple(3, 3);
        bd.add(npt);
        npt = new NodePortTuple(4, 1);
        bd.add(npt);
        bd = new BroadcastDomain();
        bd.setId(3);
        npt = new NodePortTuple(5, 1);
        bd.add(npt);
        npt = new NodePortTuple(6, 1);
        bd.add(npt);
        bdMap.put((long)2, bd);
        EasyMock.expect(topology.getHigherTopologyNodes()).andReturn(bdMap).anyTimes();
    }

    private static void setMockNeighbor() {
        Map<Long, Set<Long>> nMap = new HashMap<Long, Set<Long>>();
        Set<Long> lSet = new HashSet<Long>();
        lSet.add((long)2);
        lSet.add((long)3);
        nMap.put((long)1, lSet);
        lSet = new HashSet<Long>();
        lSet.add((long)4);
        lSet.add((long)5);
        nMap.put((long)2, lSet);
        EasyMock.expect(topology.getHigherTopologyNeighbors()).andReturn(nMap).anyTimes();
    }

    private static void setMockNextHop() {
        Map<Long, Map<Long, Long>> nMap = new HashMap<Long, Map<Long, Long>>();
        Map<Long, Long> iMap = new HashMap<Long, Long>();
        iMap.put((long)1, (long)2);
        iMap.put((long)3, (long)4);
        nMap.put((long)5, iMap);
        iMap = new HashMap<Long, Long>();
        iMap.put((long)6, (long)7);
        nMap.put((long)8, iMap);
        EasyMock.expect(topology.getHigherTopologyNextHops()).andReturn(nMap).anyTimes();
    }

    private static void setMockLayerTwoDomains() {
        Map<Long, Long> lMap = new HashMap<Long, Long>();
        lMap.put((long)1, (long)2);
        lMap.put((long)3, (long)4);
        EasyMock.expect(topology.getL2DomainIds()).andReturn(lMap).anyTimes();
    }

    @Test
    public void testExternalBroadcastInterfaces() throws Exception {
        setupMockSwitches();
        addBroadcastDomains();
        EasyMock.replay(topology);
        this.test("BigDBRestTestTopologyExternalBroadcastInterfaces", EXTERNAL_LINKS_URL);
    }

    @Test
    public void testDebugAllowedPortsUnicast() throws Exception {
        setupMockSwitches();
        setupAllowedUnicastPorts();
        EasyMock.replay(topology);
        this.test("BigDBRestTestTopologyDebugAllowedPortsUnicast", DEBUG_ALLOWED_PORTS_UNICAST);
    }

    @Test
    public void testDebugAllowedPortsBroadcast() throws Exception {
        setupMockSwitches();
        setupAllowedBroadcastPorts();
        EasyMock.replay(topology);
        this.test("BigDBRestTestTopologyDebugAllowedPortsBroadcast", DEBUG_ALLOWED_PORTS_BROADCAST);
    }

    @Test
    public void testDebugAllowedPortsExternal() throws Exception {
        setupMockSwitches();
        setupAllowedExternalPorts();
        EasyMock.replay(topology);
        this.test("BigDBRestTestTopologyDebugAllowedPortsExternal", DEBUG_ALLOWED_PORTS_EXTERNAL);
    }

    @Test
    public void testDebugHigherTopologyDomainOpenFlow() throws Exception {
        setupMockSwitches();
        setMockOpenFlowDomain();
        EasyMock.replay(topology);
        this.test("BigDBRestTestTopologyDebugHigherTopologyDomainOpenFlow", DEBUG_HIGHER_TOPOLOGY_OPENFLOW);
    }

    @Test
    public void testDebugHigherTopologyDomainBroadcast() throws Exception {
        setupMockSwitches();
        setMockBroadcastDomain();
        EasyMock.replay(topology);
        this.test("BigDBRestTestTopologyDebugHigherTopologyDomainBroadcast", DEBUG_HIGHER_TOPOLOGY_BROADCAST);
    }

    @Test
    public void testDebugHigherTopologyNeighbors() throws Exception {
        setupMockSwitches();
        setMockNeighbor();
        EasyMock.replay(topology);
        this.test("BigDBRestTestTopologyDebugHigherTopologyNeighbors", DEBUG_HIGHER_TOPOLOGY_NEIGHBORS);
    }

    @Test
    public void testDebugHigherTopologyNextHop() throws Exception {
        setupMockSwitches();
        setMockNextHop();
        EasyMock.replay(topology);
        this.test("BigDBRestTestTopologyDebugHigherTopologyNextHop", DEBUG_HIGHER_TOPOLOGY_NEXT_HOP);
    }

    @Test
    public void testDebugHigherTopologyLayerTwoDomains() throws Exception {
        setupMockSwitches();
        setMockLayerTwoDomains();
        EasyMock.replay(topology);
        this.test("BigDBRestTestTopologyDebugHigherLayerTwoDomains", DEBUG_HIGHER_TOPOLOGY_LAYER_TWO_DOMAINS);
    }
}
