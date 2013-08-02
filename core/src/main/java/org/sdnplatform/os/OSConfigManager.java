package org.sdnplatform.os;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.bigdb.BigDBException;
import net.bigdb.auth.AuthContext;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeSet;
import net.bigdb.data.ServerDataSource;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.hook.WatchHook;
import net.bigdb.query.Query;
import net.bigdb.service.Treespace;
import net.bigdb.util.Path;
import net.floodlightcontroller.bigdb.IBigDBService;
import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.sdnplatform.os.WrapperOutput.Status;
import org.sdnplatform.os.linux.ubuntu.UbuntuPlatformProvider;
import org.sdnplatform.os.model.OSAction;
import org.sdnplatform.os.model.OSConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

/**
 * Manage the configuration of the underlying operating system based on 
 * configuration information stored in the configuration backend.
 * @author readams
 */
@LogMessageCategory("OS Configuration")
public
class OSConfigManager implements IOSConfigService, IFloodlightModule {
    protected static final Logger logger =
            LoggerFactory.getLogger(OSConfigManager.class.getName());
    private ObjectMapper mapper = new ObjectMapper();
    private ObjectReader reader = mapper.reader(WrapperOutput.class);

    private IBigDBService dbService;
    private IThreadPoolService threadPool;
    
    /**
     * A list of all available platform providers.  In theory we could make
     * the provider another floodlight module, but this should be sufficient
     * for now.
     */
    private static IPlatformProvider[] providers = 
        {new UbuntuPlatformProvider()};
    
    /**
     * The currently-active platform provider
     */
    protected IPlatformProvider provider;
    
    /**
     * The path of the privileged wrapper script that will install the
     * configuration changes into the system.  Relative to current process
     * working directory
     */
    private String privedWrapper;
    
    /**
     * Base path to use.  Use '/' to apply to the real system.  Relative to 
     * current process working directory
     */
    private String basePath;
    
    /**
     * A path where the cached configuration can be stored.  If this is 
     * set we can avoid unnecessary churn.  Relative to basePath.
     */
    private String cachePath;
    
    /**
     * Attempt to run the subprocess with elevated privileges
     */
    private boolean runWithPrivs = true;
    
    /**
     * If <code>true</code>, don't execute any system subprocesses
     * in the privileged wrapper.
     */
    private boolean dryRun = false;

    /**
     * Asynchronous task for applying the OS configuration in response to
     * an event.
     */
    SingletonTask applyConfigTask;
    
    /**
     * Delay before applying configuration updates;
     */
    private static final int CONFIG_DELAY = 250;
    
    // ****************
    // IOSConfigService
    // ****************

    @Override
    public WrapperOutput applyConfiguration(OSConfig config) {
        return doApply(config, false);
    }

    @Override
    public WrapperOutput applyConfiguration(DataNode config) {
        return doApply(config, false);
    }
    
    @Override
    public WrapperOutput applyAction(OSAction action) {
        return doApply(action, true);
    }
    
    // *****************
    // IFloodlightModule
    // *****************
    
    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IOSConfigService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m =
            new HashMap<Class<? extends IFloodlightService>,
                        IFloodlightService>();
        // We are the class that implements the service
        m.put(IOSConfigService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IThreadPoolService.class);
        l.add(IBigDBService.class);
        return l;
    }

    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                       message="Platform provider {name} threw a runtime exception",
                       recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG),
        @LogMessageDoc(level="WARN",
                       message="Current OS configuration not supported",
                       explanation="Making changes to your OS configuration " +
                                   "is not be supported"),
        @LogMessageDoc(level="WARN",
                       message="Privileged wrapper path not set; OS configuration " + 
                               "disabled",
                       explanation="The privileged wrapper is needed to " + 
                                   "write OS configuration changes to the " + 
                                   "system",
                       recommendation="Set the privilegedWrapper property")
    })
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        threadPool = context.getServiceImpl(IThreadPoolService.class);
        dbService = context.getServiceImpl(IBigDBService.class);
        
        Map<String, String> config = context.getConfigParams(this);
        privedWrapper = config.get("privedWrapper");
        basePath = config.get("basePath");
        cachePath = config.get("cachePath");
        runWithPrivs = !"false".equals(config.get("runWithPrivs"));
        dryRun = "true".equals(config.get("dryRun"));

        if (this.privedWrapper == null) {
            logger.warn("Privileged wrapper path not set; OS configuration " + 
                        "disabled");
            return;
        }

        if (basePath == null) {
            basePath = "/";
        }
        if (cachePath == null) {
            cachePath = new File(new File(basePath), 
                                 "etc/osconfigCache.json").getAbsolutePath();
        }
        
        for (IPlatformProvider provider : providers) {
            try {
                if (provider.isApplicable(new File(basePath))) {
                    this.provider = provider;
                    break;
                }
            } catch (Exception e) {
                logger.error("Platform provider {} threw a runtime exception", 
                             provider.getClass().getName(), e);
            }
        }

        if (this.provider == null) {
            logger.warn("Current OS configuration not supported");
        }
    }

    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="INFO",
                       message="Performed system configuration action: {action}",
                       explanation="The controller OS configuration was modified"),
        @LogMessageDoc(level="INFO",
                       message="Overall system configuration status: {status}",
                       explanation="The controller OS configuration was modified"),
    })
    public void startUp(FloodlightModuleContext context) 
            throws FloodlightModuleException {
        Runnable applyConfig = new Runnable() {
            @Override
            public void run() {
                try {
                    Treespace t = dbService.getControllerTreespace();
                    Query osConfigQ = Query.parse("/os/config");
                    DataNodeSet dns = t.queryData(osConfigQ, AuthContext.SYSTEM);
                    DataNode osConfig = dns.getSingleDataNode();
                    if (osConfig.isNull()) return;
                    WrapperOutput output = applyConfiguration(osConfig);
                    
                    if (!output.succeeded()) {
                        // XXX - FIXME - this output really needs to go to
                        // an asynchronous alert mechanism
                        logger.error("Could not apply OS configuration: {}", 
                                     output.getOverallStatus());
                    }
                } catch (BigDBException e) {
                    logger.warn("Failed to read OS config", e);
                }
            }
        };
        applyConfigTask = new SingletonTask(threadPool.getScheduledExecutor(), 
                                            applyConfig);

        try {
            ServerDataSource controllerDataSource =
                    dbService.getControllerDataSource();
            if (controllerDataSource != null) {
                controllerDataSource.registerDynamicDataHooksFromClass(
                    new Path("/os/action"), Path.EMPTY_PATH, 
                    OSActionResource.class);
            }
            
            Treespace t = dbService.getControllerTreespace();
            if (t != null) {
                WatchHook watchHook = new OSConfigWatchHook();
                LocationPathExpression path = 
                        LocationPathExpression.parse("/os/config");
                t.getHookRegistry().registerWatchHook(path, false, watchHook);
                
                applyConfigTask.reschedule(CONFIG_DELAY, TimeUnit.MILLISECONDS);
            }
        } catch (BigDBException e) {
            throw new FloodlightModuleException(e);
        }
    }

    // *************
    // Local methods
    // *************
  
    private WrapperOutput doApply(Object config, boolean action) {
        WrapperOutput wo = new WrapperOutput();

        if (privedWrapper == null || provider == null) {
            return WrapperOutput.error(Status.ARG_ERROR,
                                       "privedWrapper or provider not set");
        }

        Process p = null;
        try {
            List<String> args = provider.getWrapperArgs(privedWrapper, 
                                                        basePath, 
                                                        cachePath,
                                                        runWithPrivs,
                                                        dryRun,
                                                        action);
            if (!runWithPrivs) {
                byte[] configBytes = mapper.writeValueAsBytes(config);
                ByteArrayInputStream bais = 
                        new ByteArrayInputStream(configBytes);
                args.remove(0);
                args.remove(0);
                args.remove(0);
                logger.debug("Directly applying os action {}", args);
                wo = OSConfigWrapper.apply(args.toArray(new String[args.size()]), 
                                           bais);
            } else {
                logger.debug("Executing subprocess {}", args);
                p = execPrivedWrapper(args);
                mapper.writeValue(p.getOutputStream(), config);
                wo = reader.readValue(p.getInputStream());
            }
        } catch (JsonGenerationException e) {
            wo.add(WrapperOutput.error(Status.SERIALIZATION_ERROR, e));
        } catch (JsonMappingException e) {
            wo.add(WrapperOutput.error(Status.SERIALIZATION_ERROR, e));
        } catch (IOException e) {
            wo.add(WrapperOutput.error(Status.IO_ERROR, e));
        } finally {
            if (p != null) {
                try {
                    p.waitFor();
                    p.destroy();
                } catch (InterruptedException e) {
                    wo.add(WrapperOutput.error(Status.SUBPROCESS_ERROR, e));
                }
                logger.debug("Subprocess exit value {}", p.exitValue());
            }
        }

        for (WrapperOutput.Item i : wo.getItems()) {
            logger.info("Performed system configuration action: {}", i);
        }
        logger.info("Overall system configuration status: {}", 
                    wo.getOverallStatus());
        
        return wo;
    }

    private Process execPrivedWrapper(List<String> args) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.environment().clear();
        return pb.start();
    }
    
    private class OSConfigWatchHook implements WatchHook {

        @Override
        public void watch(Context context) {
            logger.debug("OS configuration modified; scheduling config task");
            applyConfigTask.reschedule(CONFIG_DELAY, TimeUnit.MILLISECONDS);
        }
        
    }
}
