    package org.projectfloodlight.db.auth.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.rest.BigDBRestAPITestBase;
import org.projectfloodlight.db.rest.auth.LoginResourceRestAPITest;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Method;
import org.restlet.engine.header.Header;
import org.restlet.engine.header.HeaderConstants;
import org.restlet.resource.ClientResource;
import org.restlet.util.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/** Test features related to 'show session'
 *
 * Cribbed from AndiW's LoginResourceRestAPITest
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public class ShowSessionTest extends BigDBRestAPITestBase {
    protected final static Logger logger =
            LoggerFactory.getLogger(LoginResourceRestAPITest.class);
    private static String restLoBaseUrl;
    private static String restLoUrl;
    private static String extAddr;
    private static String restExtBaseUrl;
    private static String restExtUrl;
    private String sessionCookie = null;
    private String lastAddress = null;

    static public String getHostAddress() {

        Enumeration<NetworkInterface> nl;
        try {
            nl = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            logger.error("cannot get network interfaces", e);
            return null;
        }
        for (; nl.hasMoreElements();) {
            NetworkInterface n = nl.nextElement();
            logger.debug("interface {}", n.getName());
            Enumeration<InetAddress> al = n.getInetAddresses();
            for (; al.hasMoreElements();) {
                InetAddress a = al.nextElement();
                if (a instanceof Inet4Address) {
                    String as = a.getHostAddress();
                    logger.debug("interface address {}", as);
                    if (!as.startsWith("127.")) {
                        return as;
                    }
                }
            }
        }

        logger.error("cannot get host address");
        return null;
    }

    @BeforeClass
    public static void setUp() throws Exception {
        dbService = defaultService();
        extAddr = getHostAddress();
        authConfig = defaultAuthConfig()
            .setParam(AuthConfig.PROXY_WHITELIST, 
                      ImmutableSet.of("127.0.0.1", extAddr));
        setupBaseClass();
        
        restLoBaseUrl = REST_SERVER;
        restLoUrl = REST_URL;

        extAddr = getHostAddress();
        assertNotNull(extAddr);
        logger.debug("extAddr is {}", extAddr);
        restExtBaseUrl = restLoBaseUrl.replace("localhost", extAddr);
        restExtUrl = restLoUrl.replace("localhost", extAddr);
    }

    private void doLogin(String baseUrl) throws Exception {
        ClientResource client = new ClientResource(Method.POST, baseUrl + "/api/v1/auth/login");
        Map<?,?> map = client.post("{ \"user\": \"admin\", \"password\": \"adminpw\" }", Map.class);
        assertTrue(map != null);
        assertTrue(map.containsKey("success"));
        assertTrue((Boolean) map.get("success"));
        assertTrue(map.containsKey("session_cookie"));
        assertTrue(((String) map.get("session_cookie")).length() > 12);
        sessionCookie = (String) map.get("session_cookie");
    }

    private void doGetSession(String baseUrl, String peerAddr, String clientAddr) throws Exception {
        Request req = new Request(Method.GET, baseUrl + "/core/aaa/session");
        req.getClientInfo().setAddress(peerAddr);
        Response rsp = new Response(req);
        ClientResource client = new ClientResource(req, rsp);
        @SuppressWarnings("unchecked")
        Series<Header> headers = (Series<Header>) req.getAttributes().get(HeaderConstants.ATTRIBUTE_HEADERS);
        if (headers == null) {
            headers = new Series<Header>(Header.class);
            req.getAttributes().put(HeaderConstants.ATTRIBUTE_HEADERS, headers);
        }
        headers.add("Cookie", "session_cookie=" + sessionCookie);

        if (clientAddr != null) {
            headers.add("X-Forwarded-For", clientAddr);
        }

        @SuppressWarnings("unchecked")
        List<Map<?,?>> sessions = client.get(List.class);
        assertTrue(sessions != null);

        assertEquals(sessions.size(), 1);
        Map<?,?> session = sessions.get(0);

        assertTrue(session.containsKey("id"));
        Integer idStr = (Integer) session.get("id");
        assertTrue(session.containsKey("last-address"));
        String addrStr = (String) session.get("last-address");

        logger.debug("session {} last address {}", idStr, addrStr);
        lastAddress = addrStr;
    }

    @Test
    public void testDefaultAddress() throws Exception {
        doLogin(restLoBaseUrl);
        doGetSession(restLoUrl, "127.0.0.1", null);
        assertEquals(lastAddress, "127.0.0.1");
        doGetSession(restExtUrl, extAddr, null);
        assertEquals(lastAddress, extAddr);
    }

    @Test
    public void testDefaultAddressExternal() throws Exception {
        doLogin(restExtBaseUrl);
        doGetSession(restLoUrl, "127.0.0.1", null);
        assertEquals(lastAddress, "127.0.0.1");
    }

    @Test
    public void testLocalhost() throws Exception {
        doLogin(restLoBaseUrl);
        doGetSession(restLoUrl, "127.0.0.1", "127.0.0.1");
        assertEquals(lastAddress, "127.0.0.1");
        doGetSession(restExtUrl, extAddr, "127.0.0.1");
        assertEquals(lastAddress, "127.0.0.1");
    }

    @Test
    public void testExternal() throws Exception {
        doLogin(restLoBaseUrl);
        doGetSession(restLoUrl, "127.0.0.1", "1.2.3.4");
        assertEquals(lastAddress, "1.2.3.4");
        doGetSession(restExtUrl, extAddr, "1.2.3.4");
        assertEquals(lastAddress, "1.2.3.4");
    }

    @Test
    public void testInvalidEmpty() throws Exception {
        doLogin(restLoBaseUrl);
        doGetSession(restLoUrl, "127.0.0.1", "");
        assertEquals(lastAddress, "127.0.0.1");
        doGetSession(restExtUrl, extAddr, "");
        assertEquals(lastAddress, extAddr);
    }

    @Test
    public void testInvalidIp() throws Exception {
        doLogin(restLoBaseUrl);
        doGetSession(restLoUrl, "127.0.0.1", "1.2.3.4.5");
        assertEquals(lastAddress, "127.0.0.1");
        doGetSession(restExtUrl, extAddr, "1.2.3.4.5");
        assertEquals(lastAddress, extAddr);
    }

    @Test
    public void testFqdn() throws Exception {
        doLogin(restLoBaseUrl);
        doGetSession(restLoUrl, "127.0.0.1", "www.bigswitch.com");
        assertEquals(lastAddress, "127.0.0.1");
        doGetSession(restExtUrl, extAddr, "www.bigswitch.com");
        assertEquals(lastAddress, extAddr);
    }

    @Test
    public void testInvalidFqdn() throws Exception {
        doLogin(restLoBaseUrl);
        doGetSession(restLoUrl, "127.0.0.1", "not-a-host.bigswitch.com");
        assertEquals(lastAddress, "127.0.0.1");
        doGetSession(restExtUrl, extAddr, "not-a-host.bigswitch.com");
        assertEquals(lastAddress, extAddr);
    }

    @Test
    public void testInvalidProxy() throws Exception {

        // remove teh external host from the allowed list of proxies
        Set<? extends String> oldWhitelist = authConfig.getParam(AuthConfig.PROXY_WHITELIST);
        authConfig.setParam(AuthConfig.PROXY_WHITELIST, ImmutableSet.of("127.0.0.1"));

        doLogin(restLoBaseUrl);
        doGetSession(restLoUrl, "127.0.0.1", "1.2.3.4");
        assertEquals(lastAddress, "1.2.3.4");
        doGetSession(restExtUrl, extAddr, "1.2.3.4");

        // extAddr is not allowed to proxy
        assertEquals(lastAddress, extAddr);

        authConfig.setParam(AuthConfig.PROXY_WHITELIST, oldWhitelist);
    }
}
