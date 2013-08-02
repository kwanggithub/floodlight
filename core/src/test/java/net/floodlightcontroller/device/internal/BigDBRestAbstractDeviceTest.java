package net.floodlightcontroller.device.internal;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyShort;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;

import java.util.Date;

import net.bigdb.rest.BigDBRestAPITestBase;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.device.IDeviceService;
import net.floodlightcontroller.device.IEntityClassifierService;
import net.floodlightcontroller.device.internal.DefaultEntityClassifier;
import net.floodlightcontroller.device.test.MockDeviceManager;
import net.floodlightcontroller.flowcache.IFlowReconcileService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;

import org.junit.AfterClass;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.test.MockSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BigDBRestAbstractDeviceTest extends BigDBRestAPITestBase {
    protected static Logger logger =
            LoggerFactory.getLogger(BigDBRestAbstractDeviceTest.class);

    public static String HOST_BASE_URL;
    public static String DEVICE_ORACLE_URL;
    public static String HOST_ID_URL;

    public static String DEVICE_ORACLE_ID_URL;
    public static String SAMPLE_MAC;
    public static String SAMPLE_ENTITY;
    public static String DEVICE_ORACLE_OBJ_URL;    
    public static String HOST_BASE_PATH;

    protected static ITopologyService topology;
    protected static MockDeviceManager devManager; 
    
    public static void setupModules() throws Exception {

        getMockFloodlightProvider().setRole(Role.MASTER, "");
        devManager = new MockDeviceManager();
        moduleContext.addService(IDeviceService.class, devManager);

        IFlowReconcileService mockFlowReconciler = 
                createMock(IFlowReconcileService.class);
        moduleContext.addService(IFlowReconcileService.class, 
                                 mockFlowReconciler);
        topology = createMock(ITopologyService.class);
        moduleContext.addService(ITopologyService.class, topology);
        
        moduleContext.addService(IThreadPoolService.class, 
                                 new MockThreadPoolService());
        moduleContext.addService(IEntityClassifierService.class, 
                                 new DefaultEntityClassifier());
        moduleContext.addService(ISyncService.class, 
                                 new MockSyncService());

        topology.addListener(anyObject(ITopologyListener.class));
        expectLastCall().anyTimes();
        expect(topology.isAttachmentPointPort(anyLong(), anyShort()))
               .andReturn(true).anyTimes();
        expect(topology.getL2DomainId(anyLong())).andReturn(1L).anyTimes();
        expect(topology.isConsistent(anyLong(),
                                     anyShort(),
                                     anyLong(),
                                     anyShort())).andReturn(false).anyTimes();
        expect(topology.isBroadcastDomainPort(anyLong(), anyShort()))
                .andReturn(false).anyTimes();
        expect(topology.isInSameBroadcastDomain(anyLong(), anyShort(),
                                                anyLong(), anyShort()))
                .andReturn(true).anyTimes();
        replay(topology);
    }
    
    public static void finalSetup() throws Exception {
        moduleContext = defaultModuleContext();

        setupBaseClass();
        setupModules();
        
        devManager.init(moduleContext);
        devManager.startUp(moduleContext);
        
        deviceManagerSetup(devManager);
        
        HOST_BASE_URL =
                REST_SERVER + "/api/v1/data/controller/core/device";
        DEVICE_ORACLE_URL =
                REST_SERVER + "/api/v1/data/controller/core/device-oracle";
        HOST_ID_URL =
                HOST_BASE_URL + 
                "%5Bid=\"44656661756C74456E74697479436C617373-02-000000000002-00000004\"%5D";

        DEVICE_ORACLE_ID_URL =
                DEVICE_ORACLE_URL + 
                "%5B" + "id" +
                "=\"44656661756C74456E74697479436C617373-02-000000000002-00000004\"%5D";
        SAMPLE_MAC = "E0:00:00:00:00:05";
        SAMPLE_ENTITY = "DefaultEntityClass";
        DEVICE_ORACLE_OBJ_URL =
                DEVICE_ORACLE_URL + 
                "%5Bmac=\"" + SAMPLE_MAC + "\"%5D" + 
                "%5Bentity-class-name=\"" + SAMPLE_ENTITY + "\"%5D" +
                "%5Bvlan=" + "4" + "%5D";    
        HOST_BASE_PATH = "/core/device";
    }

    @AfterClass
    public static void teardownDevice() throws Exception {
        if (moduleContext != null) {
            IThreadPoolService tp = 
                    moduleContext.getServiceImpl(IThreadPoolService.class);
            if (tp != null)
                tp.getScheduledExecutor().shutdown();
        }
    }
    
    protected static void deviceManagerSetup(MockDeviceManager deviceManager) {
        Date d = new Date(1000000000000L);
        deviceManager.learnEntity(0xFE0000000001L, (short)10, 2, 1L, 1, d);
        deviceManager.learnEntity(0xFE0000000001L, (short)10, 2, 10L, 1, d);
        deviceManager.learnEntity(0xFE0000000001L, (short)14, 2, 10L, 2, d);
        deviceManager.learnEntity(0xFE0000000001L, (short)14, 2, 1L, 2, d);

        deviceManager.learnEntity(2L, (short)4, 0x0a0b0001, 5L, 2, d);
        deviceManager.learnEntity(2L, (short)4, 0x0a0b0001, 50L, 3, d);
        deviceManager.learnEntity(2L, (short)4, 2, 50L, 3, d);
     }

}
