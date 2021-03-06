package org.projectfloodlight.sync.internal;

import java.io.File;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.debugcounter.IDebugCounterService;
import org.projectfloodlight.debugcounter.NullDebugCounter;
import org.projectfloodlight.sync.ClusterNode;
import org.projectfloodlight.sync.IStoreClient;
import org.projectfloodlight.sync.ISyncService.Scope;
import org.projectfloodlight.sync.internal.SyncManager;
import org.projectfloodlight.sync.internal.config.AuthScheme;
import org.projectfloodlight.sync.internal.config.SyncStoreCCProvider;
import org.projectfloodlight.sync.internal.util.CryptoUtil;
import org.projectfloodlight.threadpool.IThreadPoolService;
import org.projectfloodlight.threadpool.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HostAndPort;

import static org.junit.Assert.*;
import static org.projectfloodlight.sync.internal.SyncManagerTest.waitForFullMesh;
import static org.projectfloodlight.sync.internal.SyncManagerTest.waitForValue;

public class BootstrapTest {
    protected static final Logger logger =
            LoggerFactory.getLogger(BootstrapTest.class);
    
    @Rule
    public TemporaryFolder dbFolder = new TemporaryFolder();
    ArrayList<SyncManager> syncManagers;

    @After
    public void cleanup() {
        for (SyncManager m : syncManagers) {
            m.shutdown();
        }
    }

    @Test
    public void testBootstrap() throws Exception {
        syncManagers = new ArrayList<SyncManager>();
        ArrayList<IStoreClient<Short,ClusterNode>> nodeStores = 
                new ArrayList<IStoreClient<Short,ClusterNode>>();
        ArrayList<IStoreClient<String,String>> unsyncStores = 
                new ArrayList<IStoreClient<String,String>>();
        ArrayList<Short> nodeIds = new ArrayList<Short>();
        ArrayList<ClusterNode> nodes = new ArrayList<ClusterNode>();
        
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        ThreadPool tp = new ThreadPool();

        int curPort = 6699;
        
        String keyStorePath = new File(dbFolder.getRoot(), 
                                       "keystore.jceks").getAbsolutePath();
        String keyStorePassword = "bootstrapping is fun!";
        CryptoUtil.writeSharedSecret(keyStorePath, 
                                     keyStorePassword, 
                                     CryptoUtil.secureRandom(16));
        
        // autobootstrap a cluster of 4 nodes
        for (int i = 0; i < 4; i++) {
            SyncManager syncManager = new SyncManager();
            syncManagers.add(syncManager);

            fmc.addService(IThreadPoolService.class, tp);
            fmc.addService(IDebugCounterService.class, new NullDebugCounter());
            String dbPath = 
                    new File(dbFolder.getRoot(), 
                             "server" + i).getAbsolutePath();
            fmc.addConfigParam(syncManager, "dbPath", dbPath);

            tp.init(fmc);
            syncManager.init(fmc);
            tp.startUp(fmc);
            syncManager.startUp(fmc);
            syncManager.registerStore("localTestStore", Scope.LOCAL);
            syncManager.registerStore("globalTestStore", Scope.GLOBAL);
            
            IStoreClient<String, String> unsyncStore = 
                    syncManager.getStoreClient(SyncStoreCCProvider.
                                               SYSTEM_UNSYNC_STORE, 
                                               String.class, String.class);
            IStoreClient<Short, ClusterNode> nodeStore = 
                    syncManager.getStoreClient(SyncStoreCCProvider.
                                               SYSTEM_NODE_STORE, 
                                                Short.class, ClusterNode.class);
            unsyncStores.add(unsyncStore);
            nodeStores.add(nodeStore);
            
            // Note that it will end up going through a transitional state
            // where it will listen on 6642 because it will use the fallback
            // config
            unsyncStore.put("localNodePort", String.valueOf(curPort));
            unsyncStore.put(SyncStoreCCProvider.KEY_STORE_PATH, keyStorePath);
            unsyncStore.put(SyncStoreCCProvider.KEY_STORE_PASSWORD, 
                            keyStorePassword);
            unsyncStore.put(SyncStoreCCProvider.AUTH_SCHEME, 
                            AuthScheme.CHALLENGE_RESPONSE.toString());

            String curSeed = "";
            if (i > 0) {
                curSeed = HostAndPort.fromParts(nodes.get(i-1).getHostname(), 
                                                nodes.get(i-1).getPort()).
                                                toString();
            }
            // The only thing really needed for bootstrapping is to put
            // a value for "seeds" into the unsynchronized store.
            unsyncStore.put("seeds", curSeed);

            waitForValue(unsyncStore, "localNodeId", null, 
                         3000, "unsyncStore" + i);
            short nodeId = 
                    Short.parseShort(unsyncStore.getValue("localNodeId"));
            ClusterNode node = nodeStore.getValue(nodeId);
            nodeIds.add(nodeId);
            nodes.add(node);

            while (syncManager.getClusterConfig().
                    getNode().getNodeId() != nodeId) {
                Thread.sleep(100);
            }
            while (syncManager.getClusterConfig().
                    getNode().getPort() != curPort) {
                Thread.sleep(100);
            }
            for (int j = 0; j <= i; j++) {
                for (int k = 0; k <= i; k++) {
                    waitForValue(nodeStores.get(j), nodeIds.get(k), 
                                 nodes.get(k), 3000, "nodeStore" + j);
                }
            }
            curPort -= 1;
        }
        
        SyncManager[] syncManagerArr = 
                syncManagers.toArray(new SyncManager[syncManagers.size()]);
        waitForFullMesh(syncManagerArr, 5000);

        for (SyncManager syncManager : syncManagers) {
            assertEquals(syncManagers.size(), 
                         syncManager.getClusterConfig().getNodes().size());
        }        
        
        logger.info("Cluster successfully built.  Attempting reseed");
        
        // Test reseeding
        nodeStores.get(0).delete(nodeIds.get(0));
        
        for (int j = 0; j < nodeIds.size(); j++) {
            for (int k = 0; k < nodeIds.size(); k++) {
                waitForValue(nodeStores.get(j), nodeIds.get(k), 
                             nodes.get(k), 3000, "nodeStore" + j);
            }
        }
    }
}
