package net.bigdb.auth;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import net.bigdb.BigDBException;
import net.bigdb.auth.session.SimpleSessionManager;
import net.bigdb.query.Query;
import net.bigdb.service.Treespace;
import net.floodlightcontroller.bigdb.IBigDBService;
import net.floodlightcontroller.bigdb.MockBigDBService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;

import org.junit.Before;
import org.junit.Test;

public class AuditServerTest implements Auditor {

    protected AuthService authService;
    protected MockBigDBService bigDB;
    protected Treespace treespace;
    private AuditEvent savedEvent;

    @Before
    public void setUp() throws Exception {
        FloodlightModuleContext fmc = new FloodlightModuleContext();

        AuthConfig ac = new AuthConfig();
        ac.setParam(AuthConfig.AUTH_ENABLED, true);
        ac.setParam(AuthConfig.SESSION_MANAGER, SimpleSessionManager.class);

        bigDB = new MockBigDBService();
        bigDB.addModuleSchema("floodlight", "2012-10-22");
        bigDB.addModuleSchema("aaa");
        bigDB.setAuthConfig(ac);
        bigDB.init(fmc);
        fmc.addService(IBigDBService.class, bigDB);

        treespace = bigDB.getControllerTreespace();
        initAccounting();

        authService = bigDB.getService().getAuthService();
        authService.registerAuditor("testaudit", this);
    }

    protected void initAccounting()
            throws BigDBException, UnsupportedEncodingException {
        // Add users
        Query query;
        String replaceData;
        InputStream input;

        query = Query.parse("/core/aaa/accounting");
        replaceData = "[{" +
                "\"name\":\"syslog\"" +
                "}, {" +
                "\"name\":\"testaudit\"" +
                "}]";
        input = new ByteArrayInputStream(replaceData.getBytes("UTF-8"));
        treespace.replaceData(query, Treespace.DataFormat.JSON, input,
                              AuthContext.SYSTEM);
    }

    @Test
    public void testAuditor() throws Exception {
        AuditEvent event = new AuditEvent.Builder()
            .type("test_event")
            .avPair("attr1", "val1")
            .avPair("attr2", "val2")
            .build();
        event.commit();
        assertTrue(savedEvent == event);
   }

    @Override
    public void write(AuditEvent event) {
        savedEvent = event;
    }

}
