package org.sdnplatform.sync.internal.config.bigdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.data.ServerDataSource;
import net.bigdb.util.Path;
import net.floodlightcontroller.bigdb.IBigDBService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.internal.SyncManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Module that handles cluster configuration events from the REST API.
 * This is separate module from {@link SyncManager} to avoid a circular
 * dependency on the configuration layer.
 * @author readams
 */
public class SyncConfigManager implements IFloodlightModule {
    protected static final Logger logger =
            LoggerFactory.getLogger(SyncConfigManager.class.getName());
    
    protected IBigDBService dbService;
    protected ISyncService syncService;
   
    // *****************
    // IFloodlightModule
    // *****************

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IBigDBService.class);
        l.add(ISyncService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        this.dbService = context.getServiceImpl(IBigDBService.class);
        this.syncService = context.getServiceImpl(ISyncService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context)
            throws FloodlightModuleException {
        // Attach our router
        try {
            ServerDataSource controllerDataSource =
                    dbService.getControllerDataSource();
            controllerDataSource.registerDynamicDataHooksFromClass(
                    new Path("/cluster"), Path.EMPTY_PATH,
                    SyncConfigResource.class);
        } catch (BigDBException e) {
            logger.error("Error attaching BigDB resource: " + e);
        } 
    }

}
