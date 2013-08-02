package net.floodlightcontroller.bigdb;

import java.util.ArrayList;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.config.ModuleConfig;
import net.bigdb.config.RootConfig;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;

/**
 * Mock BigDB Service allows for easy initialization from unit tests
 * @author readams
 *
 */
public class MockBigDBService extends EmbeddedBigDBService {
    List<ModuleConfig> moduleConfig = new ArrayList<>();
    
    public MockBigDBService() throws BigDBException {
        super();
    }

    public void addModuleSchema(String name) {
        addModuleSchema(name, null);
    }

    public void addModuleSchema(String name, String revision) {
        ModuleConfig c = new ModuleConfig();
        c.name = name;
        c.revision = revision;
        moduleConfig.add(c);
    }
    
    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        RootConfig rc = getDefaultConfig(moduleConfig, authConfig);
        
        try {
            bigdbService.setAuthConfig(authConfig);
            bigdbService.initializeFromRootConfig(rc);
            completeInit(context);
        } catch (Exception e) {
            throw new FloodlightModuleException(e);
        }

    }
}
