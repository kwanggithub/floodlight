package org.projectfloodlight.db;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.core.module.FloodlightModuleException;
import org.projectfloodlight.core.module.IFloodlightModule;
import org.projectfloodlight.core.module.IFloodlightService;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.session.SimpleSessionManager;
import org.projectfloodlight.db.config.DataSourceConfig;
import org.projectfloodlight.db.config.DataSourceMappingConfig;
import org.projectfloodlight.db.config.ModuleConfig;
import org.projectfloodlight.db.config.RootConfig;
import org.projectfloodlight.db.config.TreespaceConfig;
import org.projectfloodlight.db.data.ServerDataSource;
import org.projectfloodlight.db.service.Service;
import org.projectfloodlight.db.service.Treespace;
import org.projectfloodlight.db.service.internal.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class EmbeddedBigDBService implements IFloodlightModule, IBigDBService {

    public static final String CONTROLLER_TREESPACE_NAME = "controller";

    protected final static Logger logger =
            LoggerFactory.getLogger(EmbeddedBigDBService.class);
    protected final ServiceImpl bigdbService;
    private ServerDataSource floodlightDataSource;
    private final String configFile;

    protected AuthConfig authConfig;

    public EmbeddedBigDBService() throws BigDBException  {
        this(null);
    }

    public EmbeddedBigDBService(String configFile) throws BigDBException  {
        this.configFile = configFile;
        this.bigdbService = new org.projectfloodlight.db.service.internal.ServiceImpl();
    }

    public static RootConfig getDefaultConfig(List<ModuleConfig> moduleConfig,
                                              AuthConfig authConfig) {
        RootConfig rc = new RootConfig();
        TreespaceConfig tc = new TreespaceConfig();
        tc.name = "controller";
        tc.modules = moduleConfig;

        DataSourceConfig dcc = new DataSourceConfig();
        dcc.implementation_class = "org.projectfloodlight.db.data.memory.MemoryDataSource";
        dcc.name = "config";
        dcc.config = true;
        tc.data_sources = Lists.newArrayList(dcc);
        
        DataSourceMappingConfig dsmc1 = new DataSourceMappingConfig();
        dsmc1.data_source = "config";
        dsmc1.predicate = "Config";
        DataSourceMappingConfig dsmc2 = new DataSourceMappingConfig();
        dsmc2.data_source = "$data-source";
        dsmc2.predicate = "!Config";
        
        tc.data_source_mappings = Lists.newArrayList(dsmc1, dsmc2);
        rc.treespaces = Lists.newArrayList(tc);
        
        if (authConfig == null) {
            authConfig = new AuthConfig().setParam(AuthConfig.SESSION_MANAGER, 
                                                   SimpleSessionManager.class);
            authConfig.setParam(AuthConfig.ENABLE_NULL_AUTHENTICATION, true);
        }
        return rc;
    }
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IBigDBService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m =
                new HashMap<Class<? extends IFloodlightService>,
                IFloodlightService>();
        // We are the class that implements the service
        m.put(IBigDBService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        try {
            Map<String, String> configParams = context.getConfigParams(this);
            String bigDBConfigFileName = configParams.get("BigDBConfigFileName");

            if(this.authConfig != null)
                bigdbService.setAuthConfig(authConfig);

            if (bigDBConfigFileName != null) {
                File bigdbConfigFile = new File(bigDBConfigFileName);
                bigdbService.initializeFromFile(bigdbConfigFile);
            } else {
                // No config file was specified and/or the floodlight config
                // directory could not be resolved, so use the default config
                // file that comes from a resource.
                bigdbService.initializeFromResource(this.configFile);
            }
            completeInit(context);
        } catch (Exception e) {
            logger.error("Failed to start bigDB service: " + e.getMessage(), e);
            // re-throw the exception
            throw new FloodlightModuleException(e.getMessage(), e);
        }
    }
    
    protected void completeInit(FloodlightModuleContext context) 
            throws Exception {
        Treespace treespace = bigdbService.getTreespace("controller");
        if (treespace == null)
            throw new Exception("Controller treespace not found");
        floodlightDataSource = new FloodlightDataSource(context, 
                                                        treespace.getSchema());
        floodlightDataSource.setTreespace(treespace);
        treespace.registerDataSource(floodlightDataSource);
    }

    public void setAuthConfig(AuthConfig authConfig) {
        this.authConfig = authConfig;
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        try {
            bigdbService.startup();
        } catch (BigDBException e) {
            throw new RuntimeException("BigDBService failed to startup: " + e.getMessage(), e);
        }
    }

    @Override
    public Service getService() {
        return bigdbService;
    }

    @Override
    public Treespace getControllerTreespace() throws TreespaceNotFoundException {
        return bigdbService.getTreespace(CONTROLLER_TREESPACE_NAME);
    }

    @Override
    public ServerDataSource getControllerDataSource() {
        return floodlightDataSource;
    }

    @Override
    public void run() throws BigDBException {
        try {
            bigdbService.run();
        } catch (BigDBException e) {
            logger.error("Failed to start bigdb rest service: " + e.toString());
            throw e;
        }
    }

    @Override
    public void stop() throws BigDBException {
        this.bigdbService.stop();
    }
}
