package org.projectfloodlight.db.service.internal;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.TreespaceNotFoundException;
import org.projectfloodlight.db.auth.AuthConfig;
import org.projectfloodlight.db.auth.AuthService;
import org.projectfloodlight.db.auth.AuthServiceImpl;
import org.projectfloodlight.db.config.RootConfig;
import org.projectfloodlight.db.config.TreespaceConfig;
import org.projectfloodlight.db.hook.AuthorizationHook;
import org.projectfloodlight.db.hook.HookRegistry;
import org.projectfloodlight.db.rest.BigDBRestApplication;
import org.projectfloodlight.db.service.Service;
import org.projectfloodlight.db.service.Treespace;
import org.restlet.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceImpl implements Service {

    protected final static Logger logger =
            LoggerFactory.getLogger(TreespaceImpl.class);

    // FIXME: Need to figure out how to find the bigdb config file that works
    // in both a dev environment and in the production deployment, i.e. where
    // the config files are in the "configuration" directory, not in the
    // "target/bin" directory. Ideally we'd find the bigdb config file relative
    // to the location of the floodlight configuration file that was used,
    // but currently I don't think there's any way to obtain that info from the
    // floodlight in the bigdb init code. Probably need to add that to the
    // core floodlight code.
    protected final static String DEFAULT_CONFIG_RESOURCE_PATH = "/bigdb.yaml";

    protected Map<String, TreespaceImpl> treespaces =
            new HashMap<String,TreespaceImpl>();

    private AuthService authService;
    private Component restletComponent;

    private AuthConfig authConfig;

    private int restServicePort;

    public ServiceImpl() {
    }

    @Override
    public Treespace getTreespace(String name) throws TreespaceNotFoundException {
        if(!treespaces.containsKey(name))
            throw new TreespaceNotFoundException(name);
        else
            return treespaces.get(name);
    }

    @Override
    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public AuthService getAuthService() {
        return authService;
    }

    public void initializeFromRootConfig(RootConfig rootConfig)
            throws BigDBException {

        if(this.authConfig == null) {
            this.authConfig = rootConfig.getAuthConfig();
        }
        if(this.authConfig.getParam(AuthConfig.AUTH_ENABLED)) {
            authService = new AuthServiceImpl(this, authConfig);
        }
        AuthorizationHook queryAuthorizationHook = authService != null ?
                authService.getDefaultQueryAuthorizationHook() : null;
        AuthorizationHook queryPreauthorizationHook = authService != null ?
                authService.getDefaultQueryPreauthorizationHook() : null;
        AuthorizationHook mutationAuthorizationHook = authService != null ?
                authService.getDefaultMutationAuthorizationHook() : null;
        AuthorizationHook mutationPreauthorizationHook = authService != null ?
                authService.getDefaultMutationPreauthorizationHook() : null;

        for (TreespaceConfig treespaceConfig: rootConfig.treespaces) {
            // FIXME: Should we continue if one treespace fails?
            TreespaceImpl treespace = new TreespaceImpl(this, treespaceConfig);
            HookRegistry hookRegistry = treespace.getHookRegistry();
            // FIXME: Will this work to have a shared auth service and
            // use the same default authorization hook(s) across multiple
            // treespaces? Not a huge issue now since we're not using multiple
            // treespaces currently, but something to keep in mind.
            if (queryPreauthorizationHook != null) {
                hookRegistry.registerAuthorizationHook(null,
                        AuthorizationHook.Operation.QUERY,
                        AuthorizationHook.Stage.PREAUTHORIZATION, false,
                        queryPreauthorizationHook);
            }
            if (queryAuthorizationHook != null) {
                hookRegistry.registerAuthorizationHook(null,
                        AuthorizationHook.Operation.QUERY,
                        AuthorizationHook.Stage.AUTHORIZATION, false,
                        queryAuthorizationHook);
            }
            if (mutationPreauthorizationHook != null) {
                hookRegistry.registerAuthorizationHook(null,
                        AuthorizationHook.Operation.MUTATION,
                        AuthorizationHook.Stage.PREAUTHORIZATION, false,
                        mutationPreauthorizationHook);
            }
            if (mutationAuthorizationHook != null) {
                hookRegistry.registerAuthorizationHook(null,
                        AuthorizationHook.Operation.MUTATION,
                        AuthorizationHook.Stage.AUTHORIZATION, false,
                        mutationAuthorizationHook);
            }

            treespaces.put(treespace.getName(), treespace);
        }
        this.restServicePort = rootConfig.rest_service_port;
        // Initialize the authservice
        if (this.authService != null) {
            this.authService.init();
        }
    }

    public void initializeFromResource(String resourcePath)
            throws BigDBException {
        if (resourcePath == null) {
            logger.debug("Initializing BigDB service from default resource path "
                         + DEFAULT_CONFIG_RESOURCE_PATH);
            resourcePath = DEFAULT_CONFIG_RESOURCE_PATH;
        } else {
            logger.debug("Initializing BigDB service from resource path " +
                         resourcePath);
        }

        RootConfig rootConfig = RootConfig.loadConfigResource(resourcePath);
        initializeFromRootConfig(rootConfig);
    }

    public void initializeFromFile(String configFilePath)
            throws BigDBException {
        initializeFromFile(new File(configFilePath));
    }

    public void initializeFromFile(File configFile)
            throws BigDBException {
        logger.debug("Initializing BigDB service from configuration "+
                     configFile);
        RootConfig rootConfig;
        if (configFile.isDirectory()) {
            rootConfig = new RootConfig();
            File[] files = configFile.listFiles();
            Arrays.sort(files);
            for (File f : files) {
                if (f.isFile() && 
                    f.getName().matches(".*\\.yaml$"))
                    rootConfig.merge(RootConfig.loadConfigFile(f));
            }
        } else {
            rootConfig = RootConfig.loadConfigFile(configFile);
        }

        initializeFromRootConfig(rootConfig);
    }

    public void stop() throws BigDBException {
        if (this.restletComponent != null) {
            try {
                this.restletComponent.stop();
            } catch (Exception e) {
                throw new BigDBException("Failed to stop bigdb restlet sertice.",
                                         e);
            }
        }
    }

    public void startup() throws BigDBException {
        for(Treespace treespace: treespaces.values()) {
            treespace.startup();
        }
        if(authService != null)
            authService.startUp();
    }

    public void run() throws BigDBException {

        // FIXME: This is bad that we're dependent on BigDBRestApplication
        // here. BigDBRestApplication should be a client of the BigDB service
        // (i.e. at the next layer up) but the service shouldn't have any
        // dependencies on the REST API. Probably the main method below should
        // be moved to BigDBRestApplication.
        if (restletComponent != null && restletComponent.isStarted()) {
            try {
                restletComponent.stop();
            } catch (Exception e) {
                logger.warn("Failed stopping the previous bigdb service: "
                            + e.getMessage());
            }
        }

        if(restServicePort > 0)
            BigDBRestApplication.setPort(restServicePort);
        restletComponent = BigDBRestApplication.run(this);
    }

    public static void main(String[] args) {
        try {
            ServiceImpl service = new ServiceImpl();
            service.initializeFromResource(null);
            service.startup();
            service.run();
        }
        catch (Exception exc) {
            logger.error("Aborting BigDB: " + exc);
        }
    }

    public void setAuthConfig(AuthConfig authConfig) {
        this.authConfig = authConfig;
    }
}
