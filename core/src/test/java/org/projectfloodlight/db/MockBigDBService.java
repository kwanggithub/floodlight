package org.projectfloodlight.db;

import java.util.ArrayList;
import java.util.List;

import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.core.module.FloodlightModuleException;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.EmbeddedBigDBService;
import org.projectfloodlight.db.config.ModuleConfig;
import org.projectfloodlight.db.config.RootConfig;

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
