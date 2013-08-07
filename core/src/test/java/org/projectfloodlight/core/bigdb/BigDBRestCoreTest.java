package org.projectfloodlight.core.bigdb;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.jboss.netty.channel.Channel;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFPhysicalPort;
import org.projectfloodlight.core.IOFSwitch;
import org.projectfloodlight.core.ImmutablePort;
import org.projectfloodlight.core.IFloodlightProviderService.Role;
import org.projectfloodlight.core.bigdb.ControllerInfoResource;
import org.projectfloodlight.core.internal.OFSwitchImpl;
import org.projectfloodlight.db.data.ServerDataSource;
import org.projectfloodlight.db.rest.BigDBRestAPITestBase;
import org.projectfloodlight.db.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigDBRestCoreTest extends BigDBRestAPITestBase {
    protected static Logger logger =
            LoggerFactory.getLogger(BigDBRestCoreTest.class);

    public static String CONTROLLER_PATH = "/core/controller";

    @BeforeClass
    public static void testSetup() throws Exception {
        dbService = defaultService();
        setupBaseClass();
        ServerDataSource controllerDataSource = dbService.getControllerDataSource();
        controllerDataSource.registerDynamicDataHooksFromClass(
                new Path("/core"), 
                new Path("controller"), ControllerInfoResource.class);
    }

    protected static IOFSwitch makeSwitchMock(long id) {
        OFSwitchImpl oldsw = new OFSwitchImpl() {
            
        };
        OFFeaturesReply featuresReply = new OFFeaturesReply();
        featuresReply.setDatapathId(id);
        featuresReply.setPorts(new ArrayList<OFPhysicalPort>());
        oldsw.setFeaturesReply(featuresReply);

        Channel channel = createNiceMock(Channel.class);
        SocketAddress sa = new InetSocketAddress(42);
        expect(channel.getRemoteAddress()).andReturn(sa).anyTimes();
        replay(channel);

        oldsw.setChannel(channel);

        ImmutablePort pp;
        ArrayList<ImmutablePort> ports = new ArrayList<ImmutablePort>();
        byte[] ha = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x06};
        pp = (new ImmutablePort.Builder())
                .setName("eth1")
                .setPortNumber((short)1)
                .setHardwareAddress(ha)
                .build();
        ports.add(pp);

        ha = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x07};
        pp = (new ImmutablePort.Builder())
                .setName("eth2")
                .setPortNumber((short)2)
                .setHardwareAddress(ha)
                .build();
        ports.add(pp);

        ha = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x08};
        pp = (new ImmutablePort.Builder())
                .setName("port1")
                .setPortNumber((short)3)
                .setHardwareAddress(ha)
                .build();
        ports.add(pp);

        oldsw.setPorts(ports);
        return oldsw;
    }

    private void setupMockSwitches() {
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
    public void testControllerRole() throws Exception {
        getMockFloodlightProvider().setRole(Role.MASTER, "Unit test");
        this.test("BigDbRestTestControllerRole",
                  CONTROLLER_PATH + "/role");
    }

    @Test
    public void testQueryController() throws Exception {
        setupMockSwitches();
        getMockFloodlightProvider().setRole(Role.MASTER, "Unit test");
        this.test("BigDBRestTestController",
                  CONTROLLER_PATH, CONTROLLER_PATH);
    }

    @Test
    public void testQueryControllerSummary() throws Exception {
        setupMockSwitches();
        this.test("BigDBRestTestControllerSummary",
                  CONTROLLER_PATH + "/summary", CONTROLLER_PATH);
    }

    @Test
    public void testQueryControllerMemory() throws Exception {
        setupMockSwitches();
        this.test("BigDBRestTestControllerMemory",
                  CONTROLLER_PATH + "/memory", CONTROLLER_PATH);
    }

    @Test
    public void testQueryControllerHealth() throws Exception {
        setupMockSwitches();
        this.test("BigDBRestTestControllerHealth",
                  CONTROLLER_PATH + "/health", CONTROLLER_PATH);
    }
}
