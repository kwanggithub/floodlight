package net.floodlightcontroller.bigdb;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.device.IDeviceService;

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
