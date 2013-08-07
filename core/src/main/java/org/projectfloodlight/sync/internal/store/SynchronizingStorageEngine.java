package org.projectfloodlight.sync.internal.store;

import org.projectfloodlight.debugcounter.IDebugCounterService;
import org.projectfloodlight.sync.Versioned;
import org.projectfloodlight.sync.ISyncService.Scope;
import org.projectfloodlight.sync.error.SyncException;
import org.projectfloodlight.sync.internal.SyncManager;
import org.projectfloodlight.sync.internal.util.ByteArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This storage engine will asynchronously replicate its data to the other
 * nodes in the cluster based on the scope of the s
 */
public class SynchronizingStorageEngine extends ListenerStorageEngine {

    protected static Logger logger =
                LoggerFactory.getLogger(SynchronizingStorageEngine.class);

    /**
     * The scope of distribution for data in this store
     */
    protected Scope scope;

    /**
     * The synchronization manager
     */
    protected SyncManager syncManager;
    
    /**
     * Allocate a synchronizing storage engine
     * @param localStorage the local storage
     * @param debugCounter the debug counter service
     * @param scope the scope for this store
     * @param rpcService the RPC service
     * @param storeName the name of the store
     */
    public SynchronizingStorageEngine(IStorageEngine<ByteArray,
                                                    byte[]> localStorage,
                                      SyncManager syncManager,
                                      IDebugCounterService debugCounter, 
                                      Scope scope) {
        super(localStorage, syncManager, debugCounter);
        this.localStorage = localStorage;
        this.syncManager = syncManager;
        this.scope = scope;
    }

    // *************************
    // StorageEngine<Key,byte[]>
    // *************************

    @Override
    public void put(ByteArray key, Versioned<byte[]> value)
            throws SyncException {
        super.put(key, value);
        if (!Scope.UNSYNCHRONIZED.equals(scope))
            syncManager.queueSyncTask(this, key, value);
    }
    
    // **************
    // Public methods
    // **************

    /**
     * Get the scope for this store
     * @return
     */
    public Scope getScope() {
        return scope;
    }
}
