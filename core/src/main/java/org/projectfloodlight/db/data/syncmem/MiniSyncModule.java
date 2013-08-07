package org.projectfloodlight.db.data.syncmem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.projectfloodlight.core.IFloodlightProviderService;
import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.core.module.FloodlightModuleException;
import org.projectfloodlight.core.module.IFloodlightModule;
import org.projectfloodlight.core.module.IFloodlightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiniSyncModule implements IFloodlightModule {
    private final static Logger logger = LoggerFactory.getLogger(MiniSyncModule.class);

    private final List<MiniSync> syncs = new CopyOnWriteArrayList<MiniSync>();
    private IFloodlightProviderService floodlight;
    private boolean isStarted;

    private static MiniSyncModule DEFAULT_INSTANCE = null;

    public synchronized static MiniSyncModule getDefault() {
        if (DEFAULT_INSTANCE == null)
            throw new IllegalStateException(
                    "MiniSync Service not initialized. Please make sure MiniSyncModule is listed in the config before any clients");

        return DEFAULT_INSTANCE;
    }

    private synchronized static void checkSetDefault(MiniSyncModule miniSyncModule) {
        if (DEFAULT_INSTANCE == null)
            DEFAULT_INSTANCE = miniSyncModule;
    }

    public MiniSyncModule() {
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        List<Class<? extends IFloodlightService>> services =
                new ArrayList<Class<? extends IFloodlightService>>();
        services.add(IFloodlightProviderService.class);
        return services;
    }

    public synchronized void addMiniSync(MiniSync sync) {
        syncs.add(sync);
        if (isStarted) {
            initMiniSync(sync);
        }
    }

    private void initMiniSync(MiniSync sync) {
        sync.setControllerId(floodlight.getControllerId());
        floodlight.addHAListener(sync);
        Map<String, String> controllerNodeIPs = floodlight.getControllerNodeIPs();
        if(!controllerNodeIPs.isEmpty()) {
            sync.setControllerNodeIPs(controllerNodeIPs.entrySet());
        } else {
            if(logger.isDebugEnabled())
                logger.debug("floodlight.getControllerNodeIPs() is empty -- not propagating to minisync");
        }
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        checkSetDefault(this);
        floodlight = context.getServiceImpl(IFloodlightProviderService.class);
    }

    @Override
    public synchronized void startUp(FloodlightModuleContext context) {
        isStarted = true;
        for(MiniSync sync: syncs) {
            if(logger.isDebugEnabled())
                logger.debug("MiniSyncModule: issuing delayed initialization for " + sync);
            initMiniSync(sync);
        }
    }

}
