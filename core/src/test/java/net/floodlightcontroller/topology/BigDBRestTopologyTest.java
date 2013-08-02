package net.floodlightcontroller.topology;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.bigdb.rest.BigDBRestAPITestBase;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.bigdb.BigDBRestSwitchTest;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkType;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkDiscoveryManagerTestClass;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.easymock.EasyMock;
import org.junit.BeforeClass;
import org.junit.Test;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.test.MockSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigDBRestTopologyTest extends BigDBRestAPITestBase {
    protected static Logger logger =
        LoggerFactory.getLogger(BigDBRestTopologyTest.class);

    public static String TOPOLOGY_BASE_URL;
    public static String LINK_URL;
    public static String DEBUG_LINK_URL;
    public static String CLUSTERS_URL;
    public static String ENABLED_INTERFACE_URL;
    public static String EXTERNAL_INTERFACE_URL;
    public static String SWITCH_EVENT_URL;
    public static String LINK_EVENT_URL;

    protected static TopologyManager topology;
    protected static LinkDiscoveryManagerTestClass linkdiscovery;

    public static int DIRECT_LINK = 1;
    public static int MULTIHOP_LINK = 2;
    public static int TUNNEL_LINK = 3;
    public static int INVALID_LINK = 4;

    @BeforeClass
    public static void testSetup() throws Exception {
        dbService = defaultService();
        dbService.addModuleSchema("topology");
        
        moduleContext = defaultModuleContext();

        topology = new TopologyManager(false);
        linkdiscovery = new LinkDiscoveryManagerTestClass();
        
        moduleContext.addService(ITopologyService.class, topology);
        moduleContext.addService(ILinkDiscoveryService.class, linkdiscovery);
        moduleContext.addService(IThreadPoolService.class, 
                                 new MockThreadPoolService());
        moduleContext.addService(ISyncService.class, new MockSyncService());
        
        setupBaseClass();
        
        linkdiscovery.init(moduleContext);
        topology.init(moduleContext);
        linkdiscovery.startUp(moduleContext);
        topology.startUp(moduleContext);
        
        getMockFloodlightProvider().setRole(Role.MASTER, "");
        
        TOPOLOGY_BASE_URL =
                REST_SERVER + "/api/v1/data/controller/topology/";
        LINK_URL = TOPOLOGY_BASE_URL + "link";
        DEBUG_LINK_URL = TOPOLOGY_BASE_URL + "debug/link";
        CLUSTERS_URL = TOPOLOGY_BASE_URL + "switch-cluster";
        ENABLED_INTERFACE_URL = TOPOLOGY_BASE_URL + "debug/enabled-interface";
        EXTERNAL_INTERFACE_URL = TOPOLOGY_BASE_URL + "external-interface";
        SWITCH_EVENT_URL = TOPOLOGY_BASE_URL + "event-history/switch";
        LINK_EVENT_URL = TOPOLOGY_BASE_URL + "event-history/link";
    }
    
    private IOFSwitch setupSwitchMock(Map<Long, IOFSwitch> swMap,
                                       long dpid, short port) throws Exception {
        IOFSwitch sw = swMap.get(dpid);
        if (sw == null) {
            logger.debug("Added switch {}", dpid);
            sw = EasyMock.createNiceMock(IOFSwitch.class);
            expect(sw.getId()).andReturn(dpid).anyTimes();
            expect(sw.getEnabledPortNumbers()).
                andReturn(buildPortListForSw(linkArray, (int)dpid)).anyTimes();
            
            byte[] addressBytes = {1, 1, 1, (byte)(dpid%255)};
            InetAddress netaddr = InetAddress.getByAddress(addressBytes);
            SocketAddress sockaddr = new InetSocketAddress(netaddr, 5678);
            expect(sw.getInetAddress()).andReturn(sockaddr).anyTimes();
            swMap.put(dpid, sw);
        }
        addPortExpect(sw, port);
        return sw;
    }

    private Collection<Short> buildPortListForSw(int [][] linkArray, int swId) {
        Collection<Short> pList = new ArrayList<Short>();

        for (int i = 0; i < linkArray.length; i++) {
            int [] r = linkArray[i];
            if (r[0] == swId) {
                if (!pList.contains((short)r[1])) pList.add((short) r[1]);
            }
            if  (r[2] == swId) {
                if (!pList.contains((short)r[3])) pList.add((short) r[3]);
            }
        }

        return pList;
    }

    private IOFSwitch addPortExpect(IOFSwitch sw, short port) {
        ImmutablePort ofp = (new ImmutablePort.Builder())
                .setName("eth" + port)
                .setPortNumber((short)port)
                .build();
        expect(sw.getPort((short)port)).andReturn(ofp);
        return sw;
    }
    
    public void createTopologyFromLinks(int [][] linkArray) throws Exception {
        topology.clear();

        long curTime = new Date(1355189241655L).getTime();

        ILinkDiscovery.LinkType type = ILinkDiscovery.LinkType.DIRECT_LINK;
        Map<Long, IOFSwitch> swMap = new HashMap<Long, IOFSwitch>();
        for (int i = 0; i < linkArray.length; i++) {
            int [] r = linkArray[i];
            setupSwitchMock(swMap, r[0], (short)r[1]);
            setupSwitchMock(swMap, r[2], (short)r[3]);            
        }
        for (IOFSwitch sw : swMap.values())
            replay(sw);
        getMockFloodlightProvider().setSwitches(swMap);

        for (int i = 0; i < linkArray.length; i++) {
            int [] r = linkArray[i];
            if (r[4] == DIRECT_LINK)
                type= ILinkDiscovery.LinkType.DIRECT_LINK;
            else if (r[4] == MULTIHOP_LINK)
                type= ILinkDiscovery.LinkType.MULTIHOP_LINK;
            else if (r[4] == TUNNEL_LINK)
                type = ILinkDiscovery.LinkType.TUNNEL;

            Link lt = new Link(r[0], (short)r[1], r[2], (short)r[3]);
            Set<Link> lSet = new HashSet<Link>();
            lSet.add(lt);
            getMockFloodlightProvider().setSwitches(swMap);

            Long lltime = null;
            Long bdtime = null;
            if (LinkType.DIRECT_LINK.equals(type) || 
                    LinkType.TUNNEL.equals(type)) {
                lltime = curTime;
            } else if (LinkType.MULTIHOP_LINK.equals(type)) {
                bdtime = curTime;
            }
            LinkInfo linfo = new LinkInfo(curTime, lltime, bdtime);
            linkdiscovery.addOrUpdateLink(lt, linfo);

            topology.addOrUpdateLink((long)r[0], (short)r[1], (long)r[2], (short)r[3], type);
        }
        topology.createNewInstance();
    }

    private static final int [][] linkArray = {
        // src sw, src port, dst sw, dst port, link type
        {1, 1, 2, 1, DIRECT_LINK},
        {2, 2, 3, 2, DIRECT_LINK},
        {3, 1, 1, 2, DIRECT_LINK},
        {2, 3, 4, 2, MULTIHOP_LINK},
        {3, 3, 4, 1, MULTIHOP_LINK},
        {3, 4, 2, 4, TUNNEL_LINK},
        {2, 4, 3, 4, TUNNEL_LINK}
    };

    @Test
    public void testLinks() throws Exception {
        createTopologyFromLinks(linkArray);
        this.test("BigDBRestTestTopologyLinks", LINK_URL);
    }

    @Test
    public void testDebugLinks() throws Exception {
        createTopologyFromLinks(linkArray);
        this.test("BigDBRestTestTopologyLinksDebug", DEBUG_LINK_URL);
    }

    @Test
    public void testEnabledInterface() throws Exception {
        createTopologyFromLinks(linkArray);
        this.test("BigDBRestTestTopologyEnabledInterface", ENABLED_INTERFACE_URL);
    }

    private static final int [][] linkArrayCluster = {
        // src sw, src port, dst sw, dst port, link type
        {1, 1, 2, 1, DIRECT_LINK},
        {2, 2, 3, 2, DIRECT_LINK},
        {3, 1, 1, 2, DIRECT_LINK},
        {3, 4, 2, 4, TUNNEL_LINK},
        {2, 4, 3, 4, TUNNEL_LINK},
        {4, 1, 4, 2, DIRECT_LINK}
    };

    @Test
    public void testClusters() throws Exception {
        createTopologyFromLinks(linkArrayCluster);
        this.test("BigDBRestTestTopologyClusters", CLUSTERS_URL);
    }

    @Test
    public void testSwitchTunnelTermination() throws Exception {
        String SWITCH_BASE_URL = BigDBRestSwitchTest.SWITCH_PATH;
        
        String switchDpid = "00:00:00:00:00:00:00:05";
        Map<String,Object> switchData = new HashMap<String,Object>();
        switchData.put("dpid", switchDpid);
        BigDBRestAPITestBase.testPutWithoutHttp(
                new JacksonRepresentation<Map<String,Object>>(switchData), SWITCH_BASE_URL);

        String uri = SWITCH_BASE_URL + "[dpid=\"" + switchDpid + "\"]/tunnel-termination";

        BigDBRestAPITestBase.testPutWithoutHttp(new JacksonRepresentation<String>("enabled"), uri);
        // Query to verify
        this.test("BigDBSwitchTunnelTermination", uri);

        testDeleteWithoutHttp(uri);
    }
}
