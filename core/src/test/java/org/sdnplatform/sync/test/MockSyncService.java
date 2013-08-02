package org.sdnplatform.sync.test;

import java.util.Collection;
import java.util.HashMap;

import org.sdnplatform.sync.ClusterNode;
import org.sdnplatform.sync.IClusterListener;
import org.sdnplatform.sync.IClusterService;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.error.UnknownStoreException;
import org.sdnplatform.sync.internal.AbstractSyncManager;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.store.IStorageEngine;
import org.sdnplatform.sync.internal.store.IStore;
import org.sdnplatform.sync.internal.store.InMemoryStorageEngine;
import org.sdnplatform.sync.internal.store.ListenerStorageEngine;
import org.sdnplatform.sync.internal.store.MappingStoreListener;
import org.sdnplatform.sync.internal.util.ByteArray;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightService;


/**
 * Mock sync service useful for testing
 * @author readams
 */
public class MockSyncService 
        extends AbstractSyncManager 
        implements IClusterService {
    /**
     * The storage engines that contain the locally-stored data
     */
    private HashMap<String,ListenerStorageEngine> localStores =
            new HashMap<String, ListenerStorageEngine>();
    
    
    // ************
    // ISyncService
    // ************

    @Override
    public void registerStore(String storeName, Scope scope)
            throws SyncException {
        ListenerStorageEngine store = localStores.get(storeName);
        if (store != null) return;
        IStorageEngine<ByteArray, byte[]> memstore =
                new InMemoryStorageEngine<ByteArray, byte[]>(storeName);
        store = new ListenerStorageEngine(memstore, this, null);
        localStores.put(storeName, store);
    }

    /**
     * Persistent stores are not actually persistent in the mock sync service
     * @see ISyncService#registerPersistentStore(String, 
     * org.sdnplatform.sync.ISyncService.Scope)
     */
    @Override
    public void registerPersistentStore(String storeName, Scope scope)
            throws SyncException {
        registerStore(storeName, scope);
    }
    
    // *****************
    // IFloodlightModule
    // *****************

    @Override
    public void init(FloodlightModuleContext context)
                    throws FloodlightModuleException {
        
    }

    @Override
    public void startUp(FloodlightModuleContext context)
            throws FloodlightModuleException {
        
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        return null;
    }
    

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> s = 
                super.getModuleServices();
        s.add(IClusterService.class);
        return s;
    }
    
    // ***************
    // IClusterService
    // ***************
    
    @Override
    public void setAuthInfo(AuthScheme authScheme, String keyStorePath,
                            String keyStorePassword) throws SyncException {

    }

    @Override
    public void setLocalDomainId(short domainId) throws SyncException {

    }

    @Override
    public void setLocalNodePort(int port) throws SyncException {

    }

    @Override
    public void setLocalNodeIface(String ifaceName) throws SyncException {

    }

    @Override
    public void setLocalNodeHost(String hostname) throws SyncException {

    }

    @Override
    public void setSeeds(String seeds) throws SyncException {

    }

    @Override
    public void reseed() throws SyncException {

    }

    @Override
    public void deleteNode(short nodeId) throws SyncException {

    }

    @Override
    public Collection<ClusterNode> getNodes() {
        return null;
    }

    @Override
    public Short getDomainLeader() {
        return null;
    }

    @Override
    public void registerListener(IClusterListener listener) {

    }

    @Override
    public void newElection(boolean rigged) {
        
    }

    @Override
    public boolean isConnected(short nodeId) {
        return false;
    }

    @Override
    public String getKeystorePath() {
        return null;
    }

    @Override
    public String getKeystorePassword() {
        return null;
    }
    
    // *******************
    // AbstractSyncManager
    // *******************
    
    @Override
    public IStore<ByteArray, byte[]>
            getStore(String storeName) throws UnknownStoreException {
        return localStores.get(storeName);
    }

    @Override
    public short getLocalNodeId() {
        return Short.MAX_VALUE;
    }

    @Override
    public void addListener(String storeName, MappingStoreListener listener)
            throws UnknownStoreException {
        ListenerStorageEngine store = localStores.get(storeName);
        if (store == null) 
            throw new UnknownStoreException("Store " + storeName + 
                                            " has not been registered");
        store.addListener(listener);
    }

    @Override
    public void shutdown() {
        
    }

    // ***************
    // MockSyncService
    // ***************
    
    /**
     * Reset to pristine condition
     */
    public void reset() {
        localStores = new HashMap<String, ListenerStorageEngine>();
    }
}
