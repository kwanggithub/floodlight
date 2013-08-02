package net.floodlightcontroller.core.bigdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bigdb.data.ServerDataSource;
import net.bigdb.rest.BigDBRestAPITestBase;
import net.bigdb.util.Path;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.internal.MockOFSwitchImpl;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.statistics.OFAggregateStatisticsReply;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFPortStatisticsReply;
import org.openflow.protocol.statistics.OFQueueStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.protocol.statistics.OFTableStatistics;
import org.openflow.util.HexString;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigDBRestSwitchTest extends BigDBRestAPITestBase {
    protected static Logger logger =
            LoggerFactory.getLogger(BigDBRestCoreTest.class);

    public static final String SWITCH_PATH = "/core/switch";
    public static final String SWITCH_PORT_PATH = "/core/switch/interface";

    
    public static  String SWITCH_BASE_URL;
    public static  String SAMPLE_DPID = "00:00:00:00:00:00:00:05";
    public static  String SWITCH_STATS_BASE_URL;
    public static  String STATS_FLOW_URL;
    public static  String STATS_DESC_URL;
    public static  String STATS_AGG_URL;
    public static  String STATS_QUEUE_URL;
    public static  String STATS_TABLE_URL;
    public static  String STATS_PORT_URL;

    public static  String CONTROLLER_BASE_URL;
    public static  String SWITCH_COUNTER_URL;
    public static  String PORT_COUNTER_URL;
    public static  String PORT_COUNTER_PATH;

    public static  String CONTROLLER_COUNTER_PATH;
   
    public BigDBRestSwitchTest() {
        super();
    }
    
    @BeforeClass
    public static void setupTest() throws Exception {
        dbService = defaultService();
        dbService.addModuleSchema("controller");
        
        setupBaseClass();
        
        getMockFloodlightProvider().setRole(Role.MASTER, "");
        
        ServerDataSource controllerDataSource =
                dbService.getControllerDataSource();
        Path corePath = new Path("/core");
        controllerDataSource.registerDynamicDataHooksFromClass(
                corePath, new Path("controller"), ControllerInfoResource.class);
        controllerDataSource.registerDynamicDataHooksFromClass(
                corePath, new Path("switch"), SwitchResource.class);
        
        SWITCH_BASE_URL =
                REST_SERVER + "/api/v1/data/controller" + SWITCH_PATH;
        SAMPLE_DPID = "00:00:00:00:00:00:00:05";
        SWITCH_STATS_BASE_URL = 
                SWITCH_BASE_URL + "[dpid=\"" + SAMPLE_DPID + "\"]/stats";
        STATS_FLOW_URL = SWITCH_STATS_BASE_URL + "/flow";
        STATS_DESC_URL = SWITCH_STATS_BASE_URL + "/desc";
        STATS_AGG_URL = SWITCH_STATS_BASE_URL + "/aggregate";
        STATS_QUEUE_URL = SWITCH_STATS_BASE_URL + "/queue";
        STATS_TABLE_URL = SWITCH_STATS_BASE_URL + "/table";
        STATS_PORT_URL = SWITCH_STATS_BASE_URL + "/interface";

        CONTROLLER_BASE_URL =
                REST_SERVER + "/api/v1/data/controller/core/controller";
        SWITCH_COUNTER_URL = SWITCH_BASE_URL + 
                "[dpid=\"" + SAMPLE_DPID + "\"]/counter";
        PORT_COUNTER_URL = SWITCH_BASE_URL + 
                "[dpid=\"" + SAMPLE_DPID + 
                "\"]/interface[name=\"eth1\"]/counter";
        PORT_COUNTER_PATH = 
                BigDBRestAPITestBase.removeURLParts(PORT_COUNTER_URL, 
                                                    SAMPLE_DPID, "1");

        CONTROLLER_COUNTER_PATH =
                BigDBRestAPITestBase.removeURLParts(CONTROLLER_BASE_URL + 
                                                    "/counter", REST_URL);
        
        setupMockSwitches();
    }

    protected static IOFSwitch makeSwitchMock(long id) {
        MockOFSwitchImpl sw = new MockOFSwitchImpl();
        sw.setId(id);
        sw.setBuffers(256);
        sw.setCapabilities(135);
        sw.setConnectedSince(new Date(1355189241655L));

        // Add ports
        Collection<ImmutablePort> portC = new ArrayList<ImmutablePort>();
        byte[] ha = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x06};
        ImmutablePort pp = (new ImmutablePort.Builder())
                .setName("eth1")
                .setPortNumber((short)1)
                .setHardwareAddress(ha)
                .build();
        portC.add(pp);

        ha = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x07};
        pp = (new ImmutablePort.Builder())
                .setName("eth2")
                .setPortNumber((short)2)
                .setHardwareAddress(ha)
                .build();
        portC.add(pp);

        ha = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x08};
        pp = (new ImmutablePort.Builder())
                .setName("port1")
                .setPortNumber((short)3)
                .setHardwareAddress(ha)
                .build();
        portC.add(pp);
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

        // Add mock stats
        List<OFStatistics> statsList = new ArrayList<OFStatistics>();
        OFFlowStatisticsReply flowStats = new OFFlowStatisticsReply();
        flowStats.setByteCount(666);
        flowStats.setCookie(666);
        flowStats.setDurationNanoseconds(666);
        flowStats.setDurationSeconds(666);
        flowStats.setHardTimeout((short) 0);
        flowStats.setIdleTimeout((short) 0);
        flowStats.setPacketCount(666);
        flowStats.setPriority((short)666);
        flowStats.setTableId((byte)0);
        OFMatch ofm = new OFMatch();
        ofm.setDataLayerSource(HexString.fromHexString("00:00:00:00:00:01"));
        ofm.setDataLayerDestination(HexString.fromHexString("00:00:00:00:00:02"));
        ofm.setDataLayerType((short) 2048);
        ofm.setDataLayerVirtualLan(Ethernet.VLAN_UNTAGGED);
        flowStats.setMatch(ofm);
        statsList.add(flowStats);
        flowStats = new OFFlowStatisticsReply();
        flowStats.setByteCount(666);
        flowStats.setCookie(666);
        flowStats.setDurationNanoseconds(666);
        flowStats.setDurationSeconds(666);
        flowStats.setHardTimeout((short) 0);
        flowStats.setIdleTimeout((short) 0);
        flowStats.setPacketCount(666);
        flowStats.setPriority((short)666);
        flowStats.setTableId((byte)0);
        List<OFAction> flActions = new ArrayList<OFAction>();
        flActions.add(new OFActionDataLayerDestination(HexString.fromHexString("00:00:00:00:00:06")));
        flActions.add(new OFActionOutput((short) 23));
        flowStats.setActions(flActions);
        ofm = new OFMatch();
        ofm.setDataLayerType((short) 2048);
        ofm.setDataLayerVirtualLan(Ethernet.VLAN_UNTAGGED);
        ofm.setNetworkDestination(IPv4.toIPv4Address("1.1.1.1"));
        ofm.setNetworkSource(IPv4.toIPv4Address("1.1.1.2"));
        flowStats.setMatch(ofm);
        statsList.add(flowStats);
        sw.addStatsRequest(OFStatisticsType.FLOW, statsList);

        statsList = new ArrayList<OFStatistics>();
        OFPortStatisticsReply portStats = new OFPortStatisticsReply();
        portStats.setPortNumber((short)1);
        portStats.setReceiveBytes(100);
        portStats.setReceivePackets(100);
        portStats.setTransmitBytes(100);
        portStats.setTransmitPackets(100);
        statsList.add(portStats);
        portStats = new OFPortStatisticsReply();
        portStats.setPortNumber((short)2);
        portStats.setReceiveBytes(100);
        portStats.setReceivePackets(100);
        portStats.setTransmitBytes(100);
        portStats.setTransmitPackets(100);
        statsList.add(portStats);
        sw.addStatsRequest(OFStatisticsType.PORT, statsList);

        statsList = new ArrayList<OFStatistics>();
        statsList.add(desc);
        sw.addStatsRequest(OFStatisticsType.DESC, statsList);

        statsList = new ArrayList<OFStatistics>();
        OFAggregateStatisticsReply aggStats = new OFAggregateStatisticsReply();
        aggStats.setByteCount(500);
        aggStats.setFlowCount(200);
        aggStats.setPacketCount(6000);
        statsList.add(aggStats);
        sw.addStatsRequest(OFStatisticsType.AGGREGATE, statsList);

        statsList = new ArrayList<OFStatistics>();
        OFTableStatistics tableStats = new OFTableStatistics();
        tableStats.setActiveCount(5);
        tableStats.setLookupCount(567);
        tableStats.setMatchedCount(123);
        tableStats.setName("t1");
        tableStats.setMaximumEntries(1000);
        tableStats.setWildcards(6822843);
        statsList.add(tableStats);
        tableStats = new OFTableStatistics();
        tableStats.setActiveCount(5);
        tableStats.setLookupCount(567);
        tableStats.setMatchedCount(123);
        tableStats.setName("t2");
        tableStats.setMaximumEntries(1000);
        tableStats.setWildcards(6822843);
        statsList.add(tableStats);
        sw.addStatsRequest(OFStatisticsType.TABLE, statsList);

        statsList = new ArrayList<OFStatistics>();
        OFQueueStatisticsReply queueStats = new OFQueueStatisticsReply();
        queueStats.setPortNumber((short) 1);
        queueStats.setQueueId(2);
        statsList.add(queueStats);
        queueStats = new OFQueueStatisticsReply();
        queueStats.setPortNumber((short) 2);
        queueStats.setQueueId(2);
        statsList.add(queueStats);
        sw.addStatsRequest(OFStatisticsType.QUEUE, statsList);

        return sw;
    }

    private static void setupMockSwitches() {
        IOFSwitch mockSwitch1 = makeSwitchMock(1L);
        IOFSwitch mockSwitch10 = makeSwitchMock(10L);
        IOFSwitch mockSwitch5 = makeSwitchMock(5L);
        IOFSwitch mockSwitch50 = makeSwitchMock(50L);
        Map<Long, IOFSwitch> switches = new HashMap<Long,IOFSwitch>();
        switches.put(1L, mockSwitch1);
        switches.put(10L, mockSwitch10);
        switches.put(5L, mockSwitch5);
        switches.put(50L, mockSwitch50);
        getMockFloodlightProvider().setSwitches(switches);
    }

    @Test
    public void testSwitches() throws Exception {
        test("BigDbRestTestSwitches", SWITCH_BASE_URL, SWITCH_PATH);
    }

    @Test
    public void testQuerySwitchId() throws Exception {
        test("BigDBRestTestSwitchId",
                  SWITCH_BASE_URL + "%5Bdpid=\"00:00:00:00:00:00:00:05\"%5D",
                  SWITCH_PATH);
    }

    @Test
    public void testQuerySwitchIdStartWith() throws Exception {
        test("BigDBRestTestSwitchIdStartWith", SWITCH_BASE_URL +
                  "%5Bstarts-with%28dpid%2C\"00:00:00:00:00:00:00:3\"%29%5D",
                  SWITCH_PATH);
    }

    @Test
    public void testQuerySwitchIdPorts() throws Exception {
        test("BigDBRestTestSwitchIdPorts", SWITCH_BASE_URL +
                  "%5Bdpid=\"00:00:00:00:00:00:00:05\"%5D/interface",
                  SWITCH_PORT_PATH);
    }

    @Test
    public void testStatsFlow() throws Exception {
        test("BigDBRestTestSwitchStatsFlow", STATS_FLOW_URL,
                               removeURLParts(SWITCH_STATS_BASE_URL, SAMPLE_DPID));
    }

    @Test
    public void testStatsDesc() throws Exception {
        test("BigDBRestTestSwitchStatsDesc", STATS_DESC_URL,
                  removeURLParts(SWITCH_STATS_BASE_URL, SAMPLE_DPID));
    }

    @Test
    public void testStatsAgg() throws Exception {
        test("BigDBRestTestSwitchStatsAggregate", STATS_AGG_URL,
                  removeURLParts(SWITCH_STATS_BASE_URL, SAMPLE_DPID));
    }

    @Test
    public void testStatsQueue() throws Exception {
        test("BigDBRestTestSwitchStatsQueue", STATS_QUEUE_URL,
                  removeURLParts(SWITCH_STATS_BASE_URL, SAMPLE_DPID));
    }

    @Test
    public void testStatsTable() throws Exception {
        test("BigDBRestTestSwitchStatsTable", STATS_TABLE_URL,
                  removeURLParts(SWITCH_STATS_BASE_URL, SAMPLE_DPID));
    }

    @Test
    public void testStatsPort() throws Exception {
        test("BigDBRestTestSwitchStatsPort", STATS_PORT_URL,
                  removeURLParts(SWITCH_STATS_BASE_URL, SAMPLE_DPID));
    }

    @Test
    public void testSwitchAlias() throws Exception {
        // FIXME: This should ideally use the real HTTP call to set the alias,
        // but there's some error with the ClientResource usage in the code
        // that's commented out below when we do that that needs to be debugged.
        Map<String, Object> switchData = new HashMap<String, Object>();
        switchData.put("dpid", "00:00:00:00:00:00:00:05");
        switchData.put("alias", "test-name");
        BigDBRestAPITestBase.testPutWithoutHttp(
                new JacksonRepresentation<Map<String,Object>>(switchData),
                SWITCH_PATH);
        // Query to verify
        String uri = SWITCH_BASE_URL + "[dpid=\"00:00:00:00:00:00:00:05\"]/alias";
        test("BigDBSwitchAliasTest", uri);
    }
}
