package org.projectfloodlight.sync.internal.store;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.After;
import org.junit.Before;
import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.debugcounter.IDebugCounterService;
import org.projectfloodlight.debugcounter.NullDebugCounter;
import org.projectfloodlight.sync.ISyncService.Scope;
import org.projectfloodlight.sync.internal.SyncManager;
import org.projectfloodlight.sync.internal.remote.RemoteSyncManager;
import org.projectfloodlight.sync.internal.store.IStore;
import org.projectfloodlight.sync.internal.util.ByteArray;
import org.projectfloodlight.threadpool.IThreadPoolService;
import org.projectfloodlight.threadpool.ThreadPool;


public class RemoteStoreTest extends AbstractStoreT<ByteArray,byte[]> {
    ThreadPool tp;
    SyncManager syncManager;
    protected final static ObjectMapper mapper = new ObjectMapper();
    
    RemoteSyncManager remoteSyncManager;

    @Before
    public void setUp() throws Exception {
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        tp = new ThreadPool();

        syncManager = new SyncManager();
        remoteSyncManager = new RemoteSyncManager();

        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(IDebugCounterService.class, new NullDebugCounter());
        fmc.addConfigParam(syncManager, "persistenceEnabled", "false");
        
        tp.init(fmc);
        syncManager.init(fmc);
        remoteSyncManager.init(fmc);

        tp.startUp(fmc);
        syncManager.startUp(fmc);
        remoteSyncManager.startUp(fmc);

        syncManager.registerStore("local", Scope.LOCAL);
    }

    @After
    public void tearDown() {
        tp.getScheduledExecutor().shutdownNow();
        tp = null;
        syncManager.shutdown();
        remoteSyncManager.shutdown();
    }

    @Override
    public IStore<ByteArray, byte[]> getStore() throws Exception {
        return remoteSyncManager.getStore("local");
    }

    @Override
    public List<byte[]> getValues(int numValues) {
        ArrayList<byte[]> r = new ArrayList<byte[]>();
        for (int i = 0; i < numValues; i++) {
            r.add(Integer.toString(i).getBytes());
        }
        return r;
    }

    @Override
    public List<ByteArray> getKeys(int numKeys) {
        ArrayList<ByteArray> r = new ArrayList<ByteArray>();
        for (int i = 0; i < numKeys; i++) {
            r.add(new ByteArray(Integer.toString(i).getBytes()));
        }
        return r;
    }
}
