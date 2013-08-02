package org.sdnplatform.sync.internal.config;

import java.util.Collections;
import java.util.Map;

import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.module.FloodlightModuleContext;

import org.sdnplatform.sync.ClusterNode;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.internal.SyncManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Provide a fallback local configuration
 * @author readams
 */
@LogMessageCategory("State Synchronization")
public class FallbackCCProvider implements IClusterConfigProvider {
    protected static final Logger logger =
            LoggerFactory.getLogger(FallbackCCProvider.class.getName());
    protected volatile boolean warned = false;
    AuthScheme authScheme;
    String keyStorePath;
    String keyStorePassword;
    boolean leaderAllowed;
    
    public FallbackCCProvider() throws SyncException {
        
    }

    @Override
    @LogMessageDoc(level="INFO",
        message="Cluster not yet configured; using fallback " + 
                "local configuration",
        explanation="No other nodes are known")
    public ClusterConfig getConfig() throws SyncException {
        if (!warned) {
            logger.info("Cluster not yet configured; using fallback local " + 
                        "configuration");
            warned = true;
        }
        return new ClusterConfig(Collections.
                                 singletonList(new ClusterNode("localhost",
                                                               6642,
                                                               Short.MAX_VALUE,
                                                               Short.MAX_VALUE)),
                                                               Short.MAX_VALUE,
                                                               "localhost",
                                                               authScheme,
                                                               keyStorePath,
                                                               keyStorePassword,
                                                               leaderAllowed);
    }

    @Override
    public void init(SyncManager syncManager, FloodlightModuleContext context) {
        Map<String, String> config = context.getConfigParams(syncManager);
        keyStorePath = config.get("keyStorePath");
        keyStorePassword = config.get("keyStorePassword");
        authScheme = AuthScheme.NO_AUTH;
        leaderAllowed = !"false".equals(config.get("fallbackLeaderAllowed"));
        try {
            authScheme = AuthScheme.valueOf(config.get("authScheme"));
        } catch (Exception e) {
            logger.debug("No auth scheme configured");
        }
    }
}
