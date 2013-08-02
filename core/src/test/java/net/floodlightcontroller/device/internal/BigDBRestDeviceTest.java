package net.floodlightcontroller.device.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.EnumSet;

import net.bigdb.BigDBException;
import net.floodlightcontroller.device.IEntityClass;
import net.floodlightcontroller.device.IDeviceService.DeviceField;
import net.floodlightcontroller.device.bigdb.BigDBDeviceOracleResource;
import net.floodlightcontroller.device.internal.Entity;
import net.floodlightcontroller.device.internal.IndexedEntity;
import net.floodlightcontroller.device.test.MockEntityClassifier;

import org.junit.BeforeClass;
import org.junit.Test;

public class BigDBRestDeviceTest extends BigDBRestAbstractDeviceTest {

    @BeforeClass
    public static void setupClass() throws Exception {
        dbService = defaultService();

        finalSetup();
    }
    
    //Testing device id generation code
    private void testDeviceIdGeneration(String entityClass, 
                                        EnumSet<DeviceField> keyFields,
                                        Entity e,
                                        String expected) 
        throws Exception {
        
        String text = "abcdefg";
        String hexTest = "61626364656667";
        String hex = IndexedEntity.stringToHexString(text);
        assertEquals(hexTest, hex);
        
        String t = IndexedEntity.hexStringToString(hex);
        assertEquals(text, t);

        String keyString = 
                IndexedEntity.getKeyString(entityClass, e, keyFields);
        IndexedEntity ee = new IndexedEntity(keyFields, e);
        assertEquals(expected, keyString);
        IndexedEntity ie = IndexedEntity.getIndexedEntityFromKeyString(keyString);
        assertEquals(ee, ie);
        
        IndexedEntity.EntityWithClassName en = 
                IndexedEntity.getNamedEntityFromKeyString(keyString);
        assertEquals(entityClass, en.getEntityClassName());
    }
    
    @Test
    public void testDeviceId() throws Exception {
        Entity e = new Entity(0x12340F01L, (short)1, 3, 11L, 5, new Date());
        EnumSet<DeviceField> keyFields = EnumSet.of(DeviceField.MAC, DeviceField.VLAN);
        String expectKeyString = IndexedEntity.stringToHexString("default") + 
                                 "-" + "02-" + "000012340F01-" + "00000001";
        this.testDeviceIdGeneration("default", keyFields, e, expectKeyString);
        // more tests
        IEntityClass ec = (new MockEntityClassifier()).classifyEntity(e);
        keyFields = ec.getKeyFields();
        expectKeyString = IndexedEntity.stringToHexString(ec.getName()) + 
                                 "-" + "0234-" + "000012340F01-" + "00000001-" +
                                 "00000000000000000B-" + "0000000005";
        this.testDeviceIdGeneration(ec.getName(), keyFields, e, expectKeyString);
    }
    
    @Test
    public void testHosts() throws Exception {
        this.test("BigDbRestTestHosts", HOST_BASE_URL, HOST_BASE_PATH);
    }
    
    @Test
    public void testQueryHostID() throws Exception {
        this.test("BigDBRestTestHostID", HOST_ID_URL, HOST_BASE_PATH);
    }
    
    @Test
    public void testQueryHostIP() throws Exception {
        this.test("BigDBRestTestHostIP", HOST_ID_URL + 
                  "%5Bip-address=\"10.11.0.1\"%5D",
                  HOST_BASE_PATH);
    }
    
    @Test
    public void testQueryHostIPStartsWith() throws Exception {
        this.test("BigDBRestTestHostIPStartsWith", HOST_BASE_URL + 
                  "%5Bstarts-with%28ip-address%2C\"10.11\"%29%5D",
                  HOST_BASE_PATH);
    }

    @Test
    public void testQueryHostMacStartsWith() throws Exception {
        this.test("BigDBRestTestHostMacStartsWith", HOST_BASE_URL + 
                  "%5Bstarts-with%28mac%2C\"fe:00\"%29%5D",
                  HOST_BASE_PATH);
    }

    @Test
    public void testQueryHostMacIPStartsWith() throws Exception {
        this.test("BigDBRestTestHostMacIPStartsWith", HOST_BASE_URL + 
                  "%5Bstarts-with%28mac%2C\"fe:00\"%29%5D" + 
                  "%5Bstarts-with%28ip-address%2C\"0.0\"%29%5D",
                  HOST_BASE_PATH);
    }

    @Test
    public void testQueryHostIdEntity() throws Exception {
        this.test("BigDBRestTestHostIdEntity", HOST_ID_URL + 
                  "/entity",
                  HOST_BASE_PATH);
    }    

    @Test
    public void testQueryHostIdAttachmentPoint() throws Exception {
        this.test("BigDBRestTestHostIdAttachmentPoint", HOST_ID_URL + 
                  "/attachment-point",
                  HOST_BASE_PATH);
    }  

    private void testDeviceOracleClass(String macStr,
                                       Long vlanLong,
                                       String entityClassName,
                                       String dpidStr,
                                       String ipStr,
                                       Short portShort) 
            throws Exception {
        BigDBDeviceOracleResource.DeviceOracle d = 
                BigDBDeviceOracleResource.createDeviceOracle(macStr, vlanLong, 
                                                     entityClassName, 
                                                     ipStr, dpidStr, 
                                                     portShort);
        Short vlanShort = vlanLong == null ? null : new Short(vlanLong.shortValue());
        Integer portInteger = portShort == null ? null : new Integer(portShort.intValue());
        assertEquals(macStr, d.getMac());
        assertEquals(vlanShort, d.getVlan());
        assertEquals(entityClassName, d.getEntityClassName());
        assertEquals(ipStr, d.getIpAddress());
        assertEquals(dpidStr, d.getSwitchDPID());
        assertEquals(portInteger, d.getSwitchPortNumber());        
    }
    @Test
    public void testDeviceOracleClass() throws Exception {
        // create device oracle with data from xpath
        String macStr = "EA:00:00:00:00:05";
        Long vlanLong = new Long(4);
        String entityClassName = "DefaultEntityClass";
        String dpidStr = "00:00:00:00:00:00:06:07";
        String ipStr = "10.0.1.1";
        Short portShort = new Short((short)1000);
        testDeviceOracleClass(macStr,
                              vlanLong,
                              entityClassName,
                              dpidStr,
                              ipStr,
                              portShort);
        macStr = null;
        try {
            testDeviceOracleClass(macStr,
                                  vlanLong,
                                  entityClassName,
                                  dpidStr,
                                  ipStr,
                                  portShort);
            assertTrue("Test failed, null mac address", false);
        } catch (BigDBException e) {
            assertTrue(true);
        }
        macStr = "EA:00:00:00:00:05";
        entityClassName = "";
        try {
            testDeviceOracleClass(macStr,
                                  vlanLong,
                                  entityClassName,
                                  dpidStr,
                                  ipStr,
                                  portShort);
            assertTrue("Failed empty entity class name.", false);
        } catch (BigDBException e) {
            assertTrue(true);
        }
    }
    
    @Test
    public void testQueryDeviceOracleDeviceId() throws Exception {
        this.test("BigDBRestDeviceOracleDeviceId", DEVICE_ORACLE_ID_URL,
                  DEVICE_ORACLE_URL);
    }

    @Test
    public void testQueryDeviceOracleObject() throws Exception {
        this.test("BigDBRestDeviceOracleObject", DEVICE_ORACLE_OBJ_URL,
                  DEVICE_ORACLE_URL);
    }
}
