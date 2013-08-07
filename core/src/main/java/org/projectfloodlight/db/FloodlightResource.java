package org.projectfloodlight.db;

import org.projectfloodlight.core.IFloodlightProviderService;
import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.counter.ICounterStoreService;
import org.projectfloodlight.device.IDeviceService;

public class FloodlightResource {
    
    // Only allow direct access to the data member (and the setter) from the
    // other classes in the package (e.g. the BigDBService implementation),
    // not the subclasses.
    // private static IFloodlightProviderService floodlightProvider;
    
    private static FloodlightModuleContext moduleContext;
    
    public static IDeviceService getDeviceService() {
        return moduleContext.getServiceImpl(IDeviceService.class);
    }
    
    public static IFloodlightProviderService getFloodlightProvider() {
        return moduleContext.getServiceImpl(IFloodlightProviderService.class);
    }
    
    public static ICounterStoreService getCounterService() {
        return moduleContext.getServiceImpl(ICounterStoreService.class);
    }
    
    public static FloodlightModuleContext getModuleContext() {
        return moduleContext;
    }
    
    static void setModuleContext(FloodlightModuleContext context) {
        FloodlightResource.moduleContext = context;
    }
}
