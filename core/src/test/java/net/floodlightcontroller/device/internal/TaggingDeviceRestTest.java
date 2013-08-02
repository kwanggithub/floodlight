package net.floodlightcontroller.device.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bigdb.BigDBException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.resource.ClientResource;

import com.fasterxml.jackson.annotation.JsonProperty;


public class TaggingDeviceRestTest extends BigDBRestAbstractDeviceTest {

    private static final String DEVICE_ID =
            "44656661756C74456E74697479436C617373-02-000000000002-00000004";

    @BeforeClass
    public static void setupClass() throws Exception {
        dbService = defaultService();
        dbService.addModuleSchema("tag");
        dbService.addModuleSchema("device-security", "2013-07-11");

        finalSetup();
    }

    public static void ensureDeviceExists(String deviceId)
            throws Exception {
        try {
            Map<String,Object> data = new HashMap<String,Object>();
            data.put("id", deviceId);
            testPostWithoutHttp(new JacksonRepresentation<Map<String,Object>>(data),
                    "/core/device");
        }
        catch (BigDBException e) {
            if (e.getErrorType() != BigDBException.Type.CONFLICT)
                throw e;
        }
    }

    public static class SecurityAttachmentPoint {
        // return "" is for bigdb:allow-empty-string true;
        // We need this since both dpid and interfaceRegex are part of the key
        String dpid;
        String interfaceRegex;
        @JsonProperty("dpid")
        public String getDpid() {
            if (dpid != null)
                return dpid;
            return "";
        }
        public void setDpid(String dpid) {
            this.dpid = dpid;
        }
        @JsonProperty("interface-regex")
        public String getInterfaceRegex() {
            if (interfaceRegex != null)
                return interfaceRegex;
            return "";
        }
        public void setInterfaceRegex(String intfaceRegex) {
            this.interfaceRegex = intfaceRegex;
        }
    }

    @Test
    public void testSecurityAttachmentPoint() throws Exception {
        ensureDeviceExists(DEVICE_ID);
        String putURI = API_PATH + HOST_BASE_PATH + "[id=\"" + DEVICE_ID +
                "\"]/security-attachment-point";
        List<SecurityAttachmentPoint> sps = new ArrayList<SecurityAttachmentPoint>();
        SecurityAttachmentPoint sp = new SecurityAttachmentPoint();
        sp.setDpid("00:00:00:00:00:00:00:05");
        sp.setInterfaceRegex("eth*");
        sps.add(sp);
        sp = new SecurityAttachmentPoint();
        sp.setDpid("00:00:00:00:00:00:00:06");
        sp.setInterfaceRegex("int*");
        sps.add(sp);

        TaggingDeviceRestTest.testPutWithoutHttp(new JacksonRepresentation<List<SecurityAttachmentPoint>>(sps),
                                putURI);
        // Query to verify
        String getUri = HOST_BASE_URL;
        this.test("BigDBDevicetestSecurityAttachmentPoint", getUri);
//        // FIXME: Ugly hack to undo the state changes made by this test.
//        // We need to do this because the REST API unit test infrastructure
//        // doesn't ensure that the state is reset back to a well-defined state
//        // at the start of the test.
//        client.delete();
        testDeleteWithoutHttp(putURI);
    }

    @Test
    public void testDeviceAlias() throws Exception {
        ensureDeviceExists(DEVICE_ID);
        String uri = HOST_BASE_PATH + "[id=\"" + DEVICE_ID + "\"]/alias";
        testPutWithoutHttp(new JacksonRepresentation<String>("test-device-name"), uri);
        // Query to verify
        this.test("BigFloodlightDeviceAliasTest", uri);
        // FIXME: Ugly hack to undo the state changes made by this test.
        // We need to do this because the REST API unit test infrastructure
        // doesn't ensure that the state is reset back to a well-defined state
        // at the start of the test.
        testDeleteWithoutHttp(uri);
    }

    public static String createHostSecurityURI(String baseUrl, String entityKey) {
        return(baseUrl + "[id=\"" + entityKey + "\"]" + "/security-ip-address");
    }

    public static void putSecurityIPs(String baseUrl, String entityKey, List<String> ips) throws Exception {
        ensureDeviceExists(entityKey);
        String putURI = createHostSecurityURI(baseUrl, entityKey);
        ClientResource client = new ClientResource(putURI);
        try {
            client.put(new JacksonRepresentation<List<String>>(ips), MediaType.APPLICATION_JSON);
            Status s = client.getStatus();
            Assert.assertEquals(Status.SUCCESS_OK, s);
        } finally {
            client.release();
        }
    }

    @Test
    public void testSecurityIPAddress() throws Exception {
        ensureDeviceExists(DEVICE_ID);
        String putURI = API_PATH + HOST_BASE_PATH + 
                "[id=\"" + DEVICE_ID + "\"]/security-ip-address";
        List<String> sps = new ArrayList<String>();
        sps.add("10.0.1.100");
        sps.add("10.0.2.100");

        TaggingDeviceRestTest.testPutWithoutHttp(new JacksonRepresentation<List<String>>(sps), putURI);
        // Query to verify
        String getUri = HOST_BASE_PATH;
        this.test("BigDBDevicetestSecurityIPAddress", getUri);
        // FIXME: Ugly hack to undo the state changes made by this test.
        // We need to do this because the REST API unit test infrastructure
        // doesn't ensure that the state is reset back to a well-defined state
        // at the start of the test.
        testDeleteWithoutHttp(putURI);
    }

}
