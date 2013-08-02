package net.floodlightcontroller.device.internal;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyShort;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.bigdb.auth.AuthConfig;
import net.bigdb.auth.AuthContext;
import net.bigdb.auth.session.SimpleSessionManager;
import net.bigdb.query.Query;
import net.bigdb.rest.BigDBRestAPITestBase;
import net.bigdb.service.Treespace;
import net.floodlightcontroller.bigdb.IBigDBService;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.device.IDevice;
import net.floodlightcontroller.device.IDeviceService;
import net.floodlightcontroller.device.IEntityClass;
import net.floodlightcontroller.device.IEntityClassifierService;
import net.floodlightcontroller.device.IDeviceService.DeviceField;
import net.floodlightcontroller.device.internal.DefaultEntityClassifier;
import net.floodlightcontroller.device.internal.Entity;
import net.floodlightcontroller.device.internal.EntityConfig;
import net.floodlightcontroller.device.internal.IndexedEntity;
import net.floodlightcontroller.device.internal.TaggingDeviceManagerImpl;
import net.floodlightcontroller.device.internal.TaggingDeviceRestTest.SecurityAttachmentPoint;
import net.floodlightcontroller.device.tag.DeviceTag;
import net.floodlightcontroller.device.tag.IDeviceTagListener;
import net.floodlightcontroller.flowcache.FlowReconcileManager;
import net.floodlightcontroller.flowcache.IFlowReconcileService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyService;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.util.HexString;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.test.MockSyncService;

import com.google.common.collect.Lists;

/**
 * We are testing the virtual routing annotation of packets. For now, we are
 * just testing the output annotation. Need to add input annotation from BVS
 * manager.
 */
@SuppressWarnings("unused")
public class TaggingDeviceManagerTest extends BigDBRestAPITestBase {
    private static TaggingDeviceManagerImpl bigDeviceManager;
    private static MockFloodlightProvider mockFloodlightProvider;
    private static ITopologyService topology;
    private static FlowReconcileManager flowReconcileMgr;

    // in our service insertion OUI
    protected static byte[] vMAC =
            Ethernet.toMACAddress("5C:16:C7:01:DE:AD");
    protected static int vIP = IPv4.toIPv4Address("192.168.1.3");
    protected static byte[] snMAC =
            Ethernet.toMACAddress("00:44:44:22:11:00");
    protected static int snIP = IPv4.toIPv4Address("192.168.1.11");

    private static final DeviceTag tag1 = new DeviceTag("bigswitch.com",
                                            "tag1Name",
                                            "tag1Value");
    private static final EntityConfig tag1Mapping =
            new EntityConfig("00:00:00:00:00:01",
                             "10",
                             "00:00:00:00:00:00:00:01",
                             "eth1");

    private static final DeviceTag tag2 = new DeviceTag("bigswitch.com",
                                            "tag2Name",
                                            "tag1Value");
    private static final EntityConfig tag2Mapping =
            new EntityConfig("00:00:00:00:00:02", null, null, null);

    private static final DeviceTag tag3 = new DeviceTag("bigswitch.com",
                                            "tag3Name",
                                            "tag3Value");
    private static final EntityConfig tag3Mapping = new EntityConfig(null,
                                                                     "15",
                                                                     null,
                                                                     null);

    private static final DeviceTag tag4 = new DeviceTag("bigswitch.com",
                                            "tag4Name",
                                            "tag4Value");
    private static final EntityConfig tag4Mapping =
            new EntityConfig(null, null, "00:00:00:00:00:00:00:02", null);

    private static final DeviceTag tag5 = new DeviceTag("bigswitch.com",
                                            "tag5Name",
                                            "tag5Value");
    private static final EntityConfig tag5Mapping =
            new EntityConfig(null, null, "00:00:00:00:00:00:00:03", "eth3");

    private static final DeviceTag tag6 = new DeviceTag("bigswitch.com",
                                            "tag6Name",
                                            "tag6Value");
    private static final EntityConfig tag6Mapping =
            new EntityConfig(null, "20", "00:00:00:00:00:00:00:04", "eth3");

    protected TaggingDeviceManagerImpl getTagManager() {
        return bigDeviceManager;
    }

    @BeforeClass
    public static void testSetup() throws Exception {
        dbService = defaultService();
        dbService.addModuleSchema("tag");
        dbService.addModuleSchema("device-security", "2013-07-11");
        
        authConfig = new AuthConfig("enabled=false");
        setupBaseClass();

        bigDeviceManager = new TaggingDeviceManagerImpl();
        mockFloodlightProvider = getMockFloodlightProvider();
        topology = createMock(ITopologyService.class);
        flowReconcileMgr = new FlowReconcileManager();

        moduleContext.addService(IFlowReconcileService.class,
                                 flowReconcileMgr);
        moduleContext.addService(ITopologyService.class, topology);
        moduleContext.addService(IEntityClassifierService.class,
                                 new DefaultEntityClassifier());
        moduleContext.addService(IDeviceService.class,
                                 bigDeviceManager);
        moduleContext.addService(IThreadPoolService.class,
                                 new MockThreadPoolService());
        moduleContext.addService(ISyncService.class, new MockSyncService());
        
        flowReconcileMgr.init(moduleContext);
        bigDeviceManager.init(moduleContext);
        bigDeviceManager.startUp(moduleContext);
        
        getMockFloodlightProvider().setRole(Role.MASTER, "");
        basePath = REST_URL;
    }

    /**
     * Clears all state in between tests and reset EasyMocks.
     */
    @After
    public void clearState() throws Exception {
        getTagManager().clearTagsFromMemory();
        getTagManager().removeAllListeners();
        reset(topology);
        bigDeviceManager.clearAntiSpoofingMemoryStructures();
        testDeleteWithoutHttp("/tag-manager");
        if (moduleContext != null) {
            IThreadPoolService tp = 
                    moduleContext.getServiceImpl(IThreadPoolService.class);
            if (tp != null)
                tp.getScheduledExecutor().shutdown();
        }
    }

    /**
     * Tests loading tags when we startup. Also tests the clear method
     *
     * @throws Exception
     *             If there was an error.
     */
    @Test
    public void testLoadTagsFromStorage() throws Exception {
        // We add tags then clear all the datastructures
        getTagManager().addTag(tag1);
        getTagManager().addTag(tag2);
        getTagManager().addTag(tag3);
        getTagManager().addTag(tag4);
        getTagManager().addTag(tag5);
        getTagManager().addTag(tag6);
        Set<DeviceTag> tSet = getTagManager().getTags();
        assertTrue(tSet.size() == 6);
        getTagManager().clearTagsFromMemory();
        tSet = getTagManager().getTags();
        assertTrue(tSet.size() == 0);
        getTagManager().loadTagsFromStorage();
        tSet = getTagManager().getTags();
        assertTrue(tSet.size() == 6);
    }

    /**
     * Tests adding and removing tags along with different mappings.
     *
     * @throws Exception
     *             If an error occured and the test should fail.
     */

    @Test
    public void testAddAndDeleteTagsAndMappings() throws Exception {
        getTagManager().addTag(tag1);
        // Let's query that it's in tagManager
        Set<DeviceTag> tSet = getTagManager().getTags();
        assertTrue(tSet.size() == 1);
        assertTrue(tSet.contains(tag1));
        getTagManager().addTag(tag2);
        tSet = getTagManager().getTags();
        assertTrue(tSet.size() == 2);
        assertTrue(tSet.contains(tag1));
        assertTrue(tSet.contains(tag2));
        tSet = getTagManager().getTagsByNamespace(tag1.getNamespace());
        assertTrue(tSet.size() == 2);
        assertTrue(tSet.contains(tag1));
        assertTrue(tSet.contains(tag2));

        // Add some mappings
        getTagManager().mapTagToHost(tag1,
                                     tag1Mapping.getMac(),
                                     null,
                                     null,
                                     null);
        tSet =
                getTagManager().getTagsByHost(tag1Mapping.getMac(),
                                              null,
                                              null,
                                              null);
        assertEquals(1, tSet.size());
        assertTrue("The tag tag1 was not found in the tagmanager",
                          tSet.contains(tag1));
        getTagManager().mapTagToHost(tag1,
                                     null,
                                     null,
                                     tag2Mapping.getDpid(),
                                     null);
        Set<EntityConfig> eSet = 
                getTagManager().tagToEntities.get(tag1.getDBKey());
        assertTrue(eSet.size() == 2);

        getTagManager().deleteTag(tag1);
        tSet = getTagManager().getTags();
        assertTrue(tSet.size() == 1);
        assertFalse(tSet.contains(tag1));
        assertTrue(tSet.contains(tag2));
    }

    /**
     * Tests tag notifications by adding listeners and adding mappings.
     *
     * @throws Exception
     */
    @Test
    public void testTagNotifications() throws Exception {
        TaggingDeviceManagerImpl tagManager = getTagManager();

        // Set up the listeners and record the expected notification
        IDeviceTagListener listener1 = EasyMock.createNiceMock(IDeviceTagListener.class);
        IDeviceTagListener listener2 = EasyMock.createNiceMock(IDeviceTagListener.class);
        listener1.tagAdded(tag2);
        listener2.tagAdded(tag2);
        replay(listener1, listener2);

        // Now try it for real
        tagManager.addListener(listener1);
        tagManager.addListener(listener2);

        // Update a new tag
        tagManager.addTag(tag2);
        tagManager.mapTagToHost(tag2, tag1Mapping.getMac(), null, null, null);

        verify(listener1);
        verify(listener2);

        reset(listener1);
        listener1.tagDeleted(tag2);
        replay(listener1);

        // Delete a tag
        tagManager.unmapTagToHost(tag2,
                                  tag1Mapping.getMac(),
                                  null,
                                  null,
                                  null);
        tagManager.deleteTag(tag2);

        verify(listener1);
    }

    private static final String ADDRESS_SPACE_AS1 = "AS1";
    private static final String ADDRESS_SPACE_FOOBAR = "foobar";
    private static String basePath = "";

    /**
     * A shim class to represent an HostSecurityAttachmentPoint row with helpers
     * to add/remove a row from storage.
     */
    protected static class HostSecurityAttachmentPointRow {
        // private String addressSpace;
        // private Long mac;
        // private Short vlan;
        // private Long dpid;
        // private String iface;
        private SecurityAttachmentPoint sps;
        // private EnumSet<DeviceField> keyFields;
        private Entity e;
        private String eKey;

        public HostSecurityAttachmentPointRow() {
            super();
        }

        public HostSecurityAttachmentPointRow(String addressSpace,
                                              Short vlan, Long mac,
                                              Long dpid, String iface,
                                              EnumSet<DeviceField> keyFields) {
            e = new Entity(mac, vlan, null, null, null, null);
            eKey = IndexedEntity.getKeyString(addressSpace, e, keyFields);
            sps = new SecurityAttachmentPoint();
            if (dpid != null) sps.setDpid(HexString.toHexString(dpid));
            sps.setInterfaceRegex(iface);
        }

        public void writeToStorage() throws Exception {
            TaggingDeviceRestTest.ensureDeviceExists(eKey);
            String putURI =
                    "/core/device[id=\"" + eKey
                            + "\"]/security-attachment-point";
            List<SecurityAttachmentPoint> spList =
                    new ArrayList<SecurityAttachmentPoint>();
            spList.add(sps);
            BigDBRestAPITestBase.testPutWithoutHttp(new JacksonRepresentation<List<SecurityAttachmentPoint>>(spList),
                                                    putURI);
        }

        public void removeFromStorage() throws Exception {
            String dpid = sps.getDpid();
            String ifaceRegex = sps.getInterfaceRegex();
            String baseUri =
                    basePath + "/core/device[id=\"" + eKey
                            + "\"]/security-attachment-point";
            String putUri = null;
            if (dpid == null) {
                putUri =
                        String.format(baseUri + "[interface-regex=\"%s\"]",
                                      ifaceRegex);
            } else {
                putUri =
                        String.format(baseUri
                                              + "[dpid=\"%s\"][interface-regex=\"%s\"]",
                                      dpid,
                                      ifaceRegex);
            }
            BigDBRestAPITestBase.testDelete(putUri);
        }
    }

    /* A shim class to represent an HostSecurityIpAddressRow with helpers
     * to add/remove a row from storage.
     */
    protected static class HostSecurityIpAddressRow {
        private final Integer ip;
        private final EnumSet<DeviceField> keyFields;
        private final Entity e;
        private final String eKey;

        /**
         * @param addressSpace
         *            Host address space
         * @param vlan
         *            Host VLAN
         * @param mac
         *            Host MAC
         * @param ip
         *            The IP to be locked down to the host
         */
        public HostSecurityIpAddressRow(String addressSpace, Short vlan,
                                        Long mac, Integer ip,
                                        EnumSet<DeviceField> kf) {
            this.ip = ip;
            this.keyFields = kf;
            e = new Entity(mac, vlan, ip, null, null, null);
            eKey = IndexedEntity.getKeyString(addressSpace, e, keyFields);
        }

        /**
         * Writes this record to storage.
         *
         * @param bigDb
         * @param entityKey
         * @throws Exception
         */
        public void writeToStorage() throws Exception {
            TaggingDeviceRestTest.ensureDeviceExists(eKey);
            String baseUrl = REST_URL + "/core/device";
            TaggingDeviceRestTest
                .putSecurityIPs(baseUrl,
                                eKey,
                                Collections.singletonList(IPv4.fromIPv4Address(ip)));
        }

        /**
         * Deletes ALL IP records for a given entity key from storage.
         *
         * @param bigDb
         * @param entityKey
         * @throws Exception
         */
        public void removeFromStorage() throws Exception {
            String baseUrl = REST_URL + "/core/device";

            Treespace t = dbService.getControllerTreespace();
            String qStr =
                    String.format("/core/device[id=\"%s\"]/security-ip-address",
                                  eKey);
            Query q = Query.parse(qStr);
            t.deleteData(q, AuthContext.SYSTEM);
        }
    }

    /**
     * An IAnswer for responding to topology.isConsistent() calls. We return
     * true if both switch/ports are equal and false otherwise
     */
    protected static class IsConsistentAnswer implements IAnswer<Boolean> {
        @Override
        public Boolean answer() throws Throwable {
            Object[] args = getCurrentArguments();
            if (args[0].equals(args[2]) && args[1].equals(args[3]))
                return true;
            else
                return false;
        }
    }

    /**
     * Verify of the given entities are allowed or not. Calls isEntityAllowed
     * for each entity that's passed in and verifies that it matches the
     * expected value in the expected boolean array.
     * "expecgted" is an array of expected values. We expect that
     * isEntityAllowed(entities[i]) == expected[i] (in the given address space)
     *
     * @param expected
     * @param entities
     * @param entityClassName
     */
    public void verifyEntityAllowed(boolean[] expected, Entity[] entities,
                                    String entityClassName) {
        assertEquals("test setup error", expected.length, entities.length);
        for(int i = 0; i < expected.length; i++) {
            assertEquals("testing entity idx " + i,
                         expected[i],
                         isEntityAllowed(entities[i], entityClassName));
        }
    }

    private void setupTopology(IAnswer<Boolean> answer,
                               boolean switch1IsAttachmentPoint) {
        reset(topology);
        topology.isConsistent(anyLong(), anyShort(), anyLong(), anyShort());
        expectLastCall().andAnswer(answer).anyTimes();
        topology.isAttachmentPointPort(eq(1L), anyShort());
        expectLastCall().andReturn(switch1IsAttachmentPoint).anyTimes();
        topology.isAttachmentPointPort(anyLong(), anyShort());
        expectLastCall().andReturn(true).anyTimes();
        replay(topology);
    }

    /**
     * A wrapper around BigDeviceManager.isEntityAllowed (the method under
     * test). This method just mocks an IEnityClass and its key fields according
     * to the current controller semantics (default AS has VLAN+MAC as key
     * field, others have just MAC as key fields).
     *
     * @param entity
     * @param entityClassName
     * @return
     */
    public boolean isEntityAllowed(Entity entity, String entityClassName) {
        IEntityClass ec = createMock(IEntityClass.class);
        expect(ec.getName()).andReturn(entityClassName).anyTimes();
        EnumSet<IDeviceService.DeviceField> keyFields;
        if (entityClassName.equals("default")) {
            keyFields =
                    EnumSet.of(IDeviceService.DeviceField.VLAN,
                               IDeviceService.DeviceField.MAC);
        } else {
            keyFields = EnumSet.of(IDeviceService.DeviceField.MAC);
        }
        expect(ec.getKeyFields()).andReturn(keyFields).anyTimes();
        replay(ec);
        boolean rv = bigDeviceManager.isEntityAllowed(entity, ec);
        verify(ec);
        return rv;
    }

    /**
     * Test anti-spoofing protecting using only IP based rules. The test
     * successively adds (and potentially) removes IP based anti-spoofing rules.
     * We test against a set of three entities.
     */

    @Test
    public void testIsEntityAllowedIpOnly() throws Exception {
        // EmbeddedBigDBService bigdb = floodlightService.getBigDBService();
        expect(topology.isAttachmentPointPort(anyLong(), anyShort())).andReturn(true)
                                                                     .anyTimes();
        replay(topology);

        Entity e1 =
                new Entity(1L,
                           null,
                           IPv4.toIPv4Address("0.0.0.1"),
                           1L,
                           1,
                           null);
        Entity e2 = new Entity(1L, null, null, 1L, 1, null);
        Entity e3 = new Entity(2L, null, 1, 1L, 1, null);
        Entity[] entities = new Entity[] { e1, e2, e3 };

        // no rules. all entities allowed in all address-spaces.
        verifyEntityAllowed(new boolean[] { true, true, true },
                            entities,
                            ADDRESS_SPACE_FOOBAR);
        verifyEntityAllowed(new boolean[] { true, true, true },
                            entities,
                            ADDRESS_SPACE_AS1);

        // progressively populate ip to mac anti-spoofing rules for AS1.
        // we check address-space foobar to ensure it's unaffected by the rules.

        // lock IP2 to Mac 3 in AS1. Doesn't affect our entities
        HostSecurityIpAddressRow r3 =
                new HostSecurityIpAddressRow(ADDRESS_SPACE_AS1,
                                             null,
                                             3L,
                                             2,
                                             EnumSet.of(DeviceField.MAC));
        r3.writeToStorage();
        verifyEntityAllowed(new boolean[] { true, true, true },
                            entities,
                            ADDRESS_SPACE_FOOBAR);
        verifyEntityAllowed(new boolean[] { true, true, true },
                            entities,
                            ADDRESS_SPACE_AS1);

        // lock IP1 to Mac 1 in AS1. Entity e3 violates this rule and
        // should thus be disallowed.
        HostSecurityIpAddressRow r4 =
                new HostSecurityIpAddressRow(ADDRESS_SPACE_AS1,
                                             null,
                                             1L,
                                             1,
                                             EnumSet.of(DeviceField.MAC,
                                                        DeviceField.IPV4));
        r4.writeToStorage();
        verifyEntityAllowed(new boolean[] { true, true, true },
                            entities,
                            ADDRESS_SPACE_FOOBAR);
        verifyEntityAllowed(new boolean[] { true, true, false },
                            entities,
                            ADDRESS_SPACE_AS1);
        r4.removeFromStorage();

        // Lock Ip1 to Mac 3 in AS1. Not both e1 and e3 violate this rule.

        HostSecurityIpAddressRow r5 =
                new HostSecurityIpAddressRow(ADDRESS_SPACE_AS1,
                                             null,
                                             3L,
                                             1,
                                             EnumSet.of(DeviceField.MAC));
        r5.writeToStorage();
        verifyEntityAllowed(new boolean[] { true, true, true },
                            entities,
                            ADDRESS_SPACE_FOOBAR);
        verifyEntityAllowed(new boolean[] { false, true, false },
                            entities,
                            ADDRESS_SPACE_AS1);

        // previous rule still in force. Now we also allow IP1 on Mac 1.
        // e1 becomes valid again
        HostSecurityIpAddressRow r6 =
                new HostSecurityIpAddressRow(ADDRESS_SPACE_AS1,
                                             null,
                                             1L,
                                             1,
                                             EnumSet.of(DeviceField.MAC));
        r6.writeToStorage();
        verifyEntityAllowed(new boolean[] { true, true, true },
                            entities,
                            ADDRESS_SPACE_FOOBAR);
        verifyEntityAllowed(new boolean[] { true, true, false },
                            entities,
                            ADDRESS_SPACE_AS1);

        // previous rules still in force. Now we also allow IP1 on Mac 2.
        // e3 is valid again (i.e., all entities are now allowed)
        HostSecurityIpAddressRow r7 =
                new HostSecurityIpAddressRow(ADDRESS_SPACE_AS1,
                                             null,
                                             2L,
                                             1,
                                             EnumSet.of(DeviceField.MAC));
        r7.writeToStorage();
        verifyEntityAllowed(new boolean[] { true, true, true },
                            entities,
                            ADDRESS_SPACE_FOOBAR);
        verifyEntityAllowed(new boolean[] { true, true, true },
                            entities,
                            ADDRESS_SPACE_AS1);
    }

    @Test
    public void testIsEntityAllowedHost2SwitchPort() throws Exception {
        String hostBaseUrl = REST_URL + "/core/device";
        IAnswer<Boolean> isConsistentAnswer = new IsConsistentAnswer();
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        ArrayList<ImmutablePort> sw1ports = new ArrayList<ImmutablePort>();
        ArrayList<ImmutablePort> sw2ports = new ArrayList<ImmutablePort>();
        for (int i = 1; i <= 2; i++) {
            ImmutablePort p = (new ImmutablePort.Builder())
                    .setName("eth" + i)
                    .setPortNumber((short) i)
                    .build();
            expect(sw1.getPort(p.getName())).andReturn(p).anyTimes();
            sw1ports.add(p);

            p = (new ImmutablePort.Builder())
                    .setName("port" + i)
                    .setPortNumber((short) i)
                    .build();
            expect(sw2.getPort(p.getName())).andReturn(p).anyTimes();
            sw2ports.add(p);
        }
        // catch-all for ports we haven't specified
        expect(sw1.getPort(EasyMock.anyObject(String.class))).andStubReturn(null);
        expect(sw2.getPort(EasyMock.anyObject(String.class))).andStubReturn(null);
        expect(sw1.getEnabledPorts()).andReturn(sw1ports).anyTimes();
        expect(sw2.getEnabledPorts()).andReturn(sw2ports).anyTimes();
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw2.getId()).andReturn(2L).anyTimes();
        ConcurrentHashMap<Long, IOFSwitch> switches =
                new ConcurrentHashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        mockFloodlightProvider.setSwitches(switches);

        replay(sw1, sw2);

        // host1 switch1 port1
        Entity h1s1p1 = new Entity(1L, null, null, 1L, 1, null);
        // host1 switch1 port2
        Entity h1s1p2 = new Entity(1L, null, null, 1L, 2, null);
        // host1 switch2 port1
        Entity h1s2p1 = new Entity(1L, null, null, 2L, 1, null);
        // host2 switch2 port2
        Entity h2s2p2 = new Entity(2L, null, null, 2L, 2, null);
        Entity[] entities = new Entity[] { h1s1p1, h1s1p2, h1s2p1, h2s2p2 };

        // Test 0
        // no config. All should be allowed
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] { true, true, true, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, true, true, true },
                            entities,
                            "foobar");

        EnumSet<DeviceField> keyFields =
                EnumSet.of(IDeviceService.DeviceField.VLAN,
                           IDeviceService.DeviceField.MAC);
        // Test 1
        // In address-space "foobar" allow MAC 1 on the following switch ports:
        // sw1,p1(eth1), sw2,p2(port2)
        HostSecurityAttachmentPointRow r =
                new HostSecurityAttachmentPointRow("foobar",
                                                   null,
                                                   1L,
                                                   1L,
                                                   "eth1",
                                                   keyFields);
        r.writeToStorage();
        r =
                new HostSecurityAttachmentPointRow("foobar",
                                                   null,
                                                   1L,
                                                   2L,
                                                   "port1",
                                                   keyFields);
        r.writeToStorage();
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] { true, true, true, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, false, true, true },
                            entities,
                            "foobar");
        verify(topology);

        // Test 2a
        // foobar is same as Test1
        // In address-space "AS1" allow MAC 2 on a non-existing switch
        r =
                new HostSecurityAttachmentPointRow("AS1",
                                                   null,
                                                   2L,
                                                   10L,
                                                   "eth1",
                                                   keyFields);
        r.writeToStorage();
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] { true, true, true, false },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, false, true, true },
                            entities,
                            "foobar");
        verify(topology);
        r.removeFromStorage();

        // Test 2b
        // foobar is same as Test1
        // same as 2a but allow MAC 2 on existing switch 1 but on a
        // non-existing port (we use a port name what would be valid on sw 2
        // though)
        // We just reuse and modify switchPorts !
        r =
                new HostSecurityAttachmentPointRow("AS1",
                                                   null,
                                                   2L,
                                                   1L,
                                                   "port1",
                                                   keyFields);
        r.writeToStorage();
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] { true, true, true, false },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, false, true, true },
                            entities,
                            "foobar");
        verify(topology);

        // Test 2c
        // foobar is same as Test1
        // same as 2b but we finally allow MAC 2 on sw2/p2
        HostSecurityAttachmentPointRow r2;
        r2 =
                new HostSecurityAttachmentPointRow("AS1",
                                                   null,
                                                   2L,
                                                   2L,
                                                   "port2",
                                                   keyFields);
        r2.writeToStorage();
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] { true, true, true, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, false, true, true },
                            entities,
                            "foobar");
        verify(topology);
        r.removeFromStorage();
        r2.removeFromStorage();

        // Test 4a
        // foobar is same as in Test1
        // AS1: Only allow MAC1 on sw1/eth1
        r =
                new HostSecurityAttachmentPointRow("AS1",
                                                   null,
                                                   1L,
                                                   1L,
                                                   "eth1",
                                                   keyFields);
        r.writeToStorage();
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] { true, false, false, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, false, true, true },
                            entities,
                            "foobar");
        verify(topology);
        r.removeFromStorage();

        // Test 4b
        // same as 2a but also allow MAC 1 on sw2/port1
        r =
                new HostSecurityAttachmentPointRow("AS1",
                                                   null,
                                                   1L,
                                                   1L,
                                                   "eth1",
                                                   keyFields);
        r.writeToStorage();
        r2 =
                new HostSecurityAttachmentPointRow("AS1",
                                                   null,
                                                   1L,
                                                   2L,
                                                   "port1",
                                                   keyFields);
        r2.writeToStorage();
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] { true, false, true, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, false, true, true },
                            entities,
                            "foobar");
        verify(topology);
        r.removeFromStorage();
        r2.removeFromStorage();

        // Test 5
        // foobar is same as in Test1
        // AS1: Only allow MAC1 on sw1/eth1. However, we declare all ports
        // on switch 1 as non-attachment point ports
        r =
                new HostSecurityAttachmentPointRow("AS1",
                                                   null,
                                                   1L,
                                                   1L,
                                                   "eth1",
                                                   keyFields);
        r.writeToStorage();
        setupTopology(isConsistentAnswer, false);
        verifyEntityAllowed(new boolean[] { true, true, false, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, true, true, true },
                            entities,
                            "foobar");
        r.removeFromStorage();
        verify(topology);

        // Test 6: regex on switch1: match all ports
        r =
                new HostSecurityAttachmentPointRow("AS1",
                                                   null,
                                                   1L,
                                                   1L,
                                                   "eth.*",
                                                   keyFields);
        r.writeToStorage();
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] { true, true, false, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, false, true, true },
                            entities,
                            "foobar");
        r.removeFromStorage();
        verifyEntityAllowed(new boolean[] { true, true, true, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, false, true, true },
                            entities,
                            "foobar");
        // r is already removed
        verify(topology);

        // Test 7: regex all switches
        r =
                new HostSecurityAttachmentPointRow("AS1",
                                                   null,
                                                   1L,
                                                   null,
                                                   ".*1",
                                                   keyFields);
        r.writeToStorage();
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] { true, false, true, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, false, true, true },
                            entities,
                            "foobar");
        r.removeFromStorage();
        verifyEntityAllowed(new boolean[] { true, true, true, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, false, true, true },
                            entities,
                            "foobar");
        // r is already removed
        verify(topology);
        verify(sw1, sw2);
    }

    /*
     * Test spoofing protection in default address space with different VLANs
     */
    @Test
    public void testIsEntityAllowedVlan() throws Exception {
        IAnswer<Boolean> isConsistentAnswer = new IsConsistentAnswer();
        IOFSwitch sw1 = createMock(IOFSwitch.class);
        IOFSwitch sw2 = createMock(IOFSwitch.class);
        ArrayList<ImmutablePort> sw1ports = new ArrayList<ImmutablePort>();
        ArrayList<ImmutablePort> sw2ports = new ArrayList<ImmutablePort>();
        for (int i = 1; i <= 2; i++) {
            ImmutablePort p = (new ImmutablePort.Builder())
                    .setName("eth" + i)
                    .setPortNumber((short) i)
                    .build();
            expect(sw1.getPort(p.getName())).andReturn(p).anyTimes();
            sw1ports.add(p);

            p = (new ImmutablePort.Builder())
                    .setName("eth" + i)
                    .setPortNumber((short) i)
                    .build();
            expect(sw2.getPort(p.getName())).andReturn(p).anyTimes();
            sw2ports.add(p);
        }
        // catch-all for ports we haven't specified
        expect(sw1.getPort(EasyMock.anyObject(String.class))).andStubReturn(null);
        expect(sw2.getPort(EasyMock.anyObject(String.class))).andStubReturn(null);
        expect(sw1.getEnabledPorts()).andReturn(sw1ports).anyTimes();
        expect(sw2.getEnabledPorts()).andReturn(sw2ports).anyTimes();
        expect(sw1.getId()).andReturn(1L).anyTimes();
        expect(sw2.getId()).andReturn(2L).anyTimes();
        ConcurrentHashMap<Long, IOFSwitch> switches =
                new ConcurrentHashMap<Long, IOFSwitch>();
        switches.put(1L, sw1);
        switches.put(2L, sw2);
        mockFloodlightProvider.setSwitches(switches);

        replay(sw1, sw2);

        Entity h1 = new Entity(1L, null, 1, 1L, 1, null);
        Entity h1vlan2 = new Entity(1L, (short) 2, 1, 1L, 1, null);
        Entity h1vlan2sw2 = new Entity(1L, (short) 2, 1, 2L, 1, null);
        Entity h2vlan2 = new Entity(2L, (short) 2, 1, 1L, 1, null);
        Entity[] entities =
                new Entity[] { h1, h1vlan2, h1vlan2sw2, h2vlan2 };

        // Test 0
        // no config. All should be allowed
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] { true, true, true, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, true, true, true },
                            entities,
                            "default");
        verify(topology);

        EnumSet<DeviceField> keyFields =
                EnumSet.of(IDeviceService.DeviceField.VLAN,
                           IDeviceService.DeviceField.MAC);
        // Test 1
        // Lock MAC 1 to nonexisting switch/port in AS1 and default. VLAN
        // should be ignored in AS1
        HostSecurityAttachmentPointRow r1 =
                new HostSecurityAttachmentPointRow("AS1",
                                                   null,
                                                   1L,
                                                   0xffL,
                                                   "eth1",
                                                   keyFields);
        HostSecurityAttachmentPointRow r2 =
                new HostSecurityAttachmentPointRow("default",
                                                   null,
                                                   1L,
                                                   0xffL,
                                                   "eth1",
                                                   keyFields);
        r1.writeToStorage();
        r2.writeToStorage();
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] { false, false, false, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { false, true, true, true },
                            entities,
                            "default");
        verify(topology);

        // Test 2: now lock MAC 1 to sw1-port1
        HostSecurityAttachmentPointRow r3 =
                new HostSecurityAttachmentPointRow("AS1",
                                                   (short) 2,
                                                   1L,
                                                   0x1L,
                                                   "eth1",
                                                   keyFields);
        HostSecurityAttachmentPointRow r4 =
                new HostSecurityAttachmentPointRow("default",
                                                   (short) 2,
                                                   1L,
                                                   0x1L,
                                                   "eth1",
                                                   keyFields);
        r3.writeToStorage();
        r4.writeToStorage();
        setupTopology(isConsistentAnswer, true);
        verifyEntityAllowed(new boolean[] { false, false, false, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { false, true, false, true },
                            entities,
                            "default");
        verify(topology);

        // Clear storage
        r1.removeFromStorage();
        r2.removeFromStorage();
        r3.removeFromStorage();
        r4.removeFromStorage();

        String hostBaseUrl = REST_URL + "/core/device";

        // Test 3: Lock IP1 to MAC 2
        HostSecurityIpAddressRow ipRow1 =
                new HostSecurityIpAddressRow("AS1", null, 2L, 1, keyFields);
        HostSecurityIpAddressRow ipRow2 =
                new HostSecurityIpAddressRow("default",
                                             null,
                                             2L,
                                             1,
                                             keyFields);
        ipRow1.writeToStorage();
        ipRow2.writeToStorage();
        verifyEntityAllowed(new boolean[] { false, false, false, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { false, false, false, false },
                            entities,
                            "default");
        verify(topology);

        // Test 4: same as Test3 plus add: Lock IP1 to MAC 2, VLAN 2
        // Not setting for AS1 since using non-default AS + vlan is illegal
        HostSecurityIpAddressRow ipRow3 =
                new HostSecurityIpAddressRow("default",
                                             (short) 2,
                                             2L,
                                             1,
                                             keyFields);
        ipRow3.writeToStorage();
        verifyEntityAllowed(new boolean[] { false, false, false, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { false, false, false, true },
                            entities,
                            "default");
        verify(topology);

        // Test 5: Same as Test 4: add: Lock IP1 to MAC 1
        HostSecurityIpAddressRow ipRow4 =
                new HostSecurityIpAddressRow("AS1", null, 1L, 1, keyFields);
        HostSecurityIpAddressRow ipRow5 =
                new HostSecurityIpAddressRow("default",
                                             null,
                                             1L,
                                             1,
                                             keyFields);
        ipRow4.writeToStorage();
        ipRow5.writeToStorage();
        verifyEntityAllowed(new boolean[] { true, true, true, true },
                            entities,
                            "AS1");
        verifyEntityAllowed(new boolean[] { true, false, false, true },
                            entities,
                            "default");
        verify(topology);

        verify(sw1, sw2);
    }

    /**
     * Add the specified HostSecurityAttachmentPointRow to the storage source
     * and then verify how many rules BigDeviceManager has read into its
     * matching structure (hostSecurityInterfaceRegexMap). This method is used
     * to check if out-of-bounds values are ignored when reading from storage.
     * Will also remove the row after we are done.
     *
     * @param r
     *            row to write to storage
     * @param expectedEntries
     *            number of expected entries in matching structure after row is
     *            added.
     * @throws Exception
     */
    public
            void
            doTestHostSecurityIpAddressRow(HostSecurityIpAddressRow r,
                                           int expectedEntries) throws Exception {
        r.writeToStorage();
        assertEquals(expectedEntries,
                            bigDeviceManager.hostSecurityIpMap.size());
        r.removeFromStorage();

    }

    /**
     * Valid range check when reading HostSecurityIpAddress config from storage.
     * Valid vlans are 1-4095. MACs must be at most 6 bytes.
     */
    @Test
    public void testHostSecurityIpAddressVlanMacRange() throws Exception {
        HostSecurityIpAddressRow ipRow;
        EnumSet<DeviceField> keyFields =
                EnumSet.of(IDeviceService.DeviceField.VLAN,
                           IDeviceService.DeviceField.MAC);
        // vlan 0: invalid. ignored.
        ipRow =
                new HostSecurityIpAddressRow("default",
                                             (short) 0,
                                             1L,
                                             1,
                                             keyFields);

        doTestHostSecurityIpAddressRow(ipRow, 0);

        // vlan 1: ok
        ipRow =
                new HostSecurityIpAddressRow("default",
                                             (short) 1,
                                             1L,
                                             1,
                                             keyFields);
        doTestHostSecurityIpAddressRow(ipRow, 1);

        // vlan 4095: ok
        ipRow =
                new HostSecurityIpAddressRow("default",
                                             (short) 4095,
                                             1L,
                                             1,
                                             keyFields);
        doTestHostSecurityIpAddressRow(ipRow, 1);

        // vlan 4096: invalid. ignored.
        ipRow =
                new HostSecurityIpAddressRow("default",
                                             (short) 4096,
                                             1L,
                                             1,
                                             keyFields);
        doTestHostSecurityIpAddressRow(ipRow, 0);

        // MAC is all "1". largest valid mac.
        ipRow =
                new HostSecurityIpAddressRow("default",
                                             (short) 1,
                                             0xffffffffffffL,
                                             1,
                                             keyFields);
        doTestHostSecurityIpAddressRow(ipRow, 1);

        // MAC is more then 48 bit. Invalid and ignored.
        ipRow =
                new HostSecurityIpAddressRow("default",
                                             (short) 1,
                                             0x1000000000000L,
                                             1,
                                             keyFields);
        doTestHostSecurityIpAddressRow(ipRow, 0);
    }

    /**
     * Add the specified HostSecurityAttachmentPointRow to the storage source
     * and then verify how many rules BigDeviceManager has read into its
     * matching structure (hostSecurityInterfaceRegexMap). This method is used
     * to check if out-of-bounds values are ignored when reading from storage.
     * Will also remove the row after we are done.
     *
     * @param r
     *            row to write to storage
     * @param expectedEntries
     *            number of expected entries in matching structure after row is
     *            added.
     */
    private
            void
            doTestHostSecurityAttachmentPointRow(HostSecurityAttachmentPointRow r,
                                                 int expectedEntries) throws Exception {
        r.writeToStorage();
        assertEquals(expectedEntries,
                            bigDeviceManager.hostSecurityInterfaceRegexMap.size());
        r.removeFromStorage();
    }

    /**
     * Valid range check when reading HostSecurityAttachmentPoint config from
     * storage. Valid vlans are 1--4095. Macs must be at most 6 bytes.
     */
    @Test
    public void
            testHostSecurityAttachmentPointVlanMacRange() throws Exception {
        HostSecurityAttachmentPointRow apRow;
        EnumSet<DeviceField> keyFields =
                EnumSet.of(IDeviceService.DeviceField.VLAN,
                           IDeviceService.DeviceField.MAC);
        // vlan 0: invalid. ignored
        apRow =
                new HostSecurityAttachmentPointRow("default",
                                                   (short) 0,
                                                   1L,
                                                   1L,
                                                   "eth1",
                                                   keyFields);

        doTestHostSecurityAttachmentPointRow(apRow, 0);

        // vlan 1: ok
        apRow =
                new HostSecurityAttachmentPointRow("default",
                                                   (short) 1,
                                                   1L,
                                                   1L,
                                                   "eth1",
                                                   keyFields);
        doTestHostSecurityAttachmentPointRow(apRow, 1);

        // vlan 4095: ok
        apRow =
                new HostSecurityAttachmentPointRow("default",
                                                   (short) 4095,
                                                   1L,
                                                   1L,
                                                   "eth1",
                                                   keyFields);
        doTestHostSecurityAttachmentPointRow(apRow, 1);

        // vlan 4096: invalid. ignored
        apRow =
                new HostSecurityAttachmentPointRow("default",
                                                   (short) 4096,
                                                   1L,
                                                   1L,
                                                   "eth1",
                                                   keyFields);
        doTestHostSecurityAttachmentPointRow(apRow, 0);

        // MAC is all "1". Still allowed.
        apRow =
                new HostSecurityAttachmentPointRow("default",
                                                   (short) 1,
                                                   0xffffffffffffL,
                                                   1L,
                                                   "eth1",
                                                   keyFields);
        doTestHostSecurityAttachmentPointRow(apRow, 1);

        // MAC has more then 48 bits. Ignored.
        apRow =
                new HostSecurityAttachmentPointRow("default",
                                                   (short) 1,
                                                   0x1000000000000L,
                                                   1L,
                                                   "eth1",
                                                   keyFields);

        doTestHostSecurityAttachmentPointRow(apRow, 0);
    }
}
