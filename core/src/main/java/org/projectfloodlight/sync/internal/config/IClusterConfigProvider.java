package org.projectfloodlight.sync.internal.config;

import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.sync.error.SyncException;
import org.projectfloodlight.sync.internal.SyncManager;

/**
 * Provides configuration for the sync service
 * @author readams
 */
public interface IClusterConfigProvider {
    /**
     * Initialize the provider with the configuration parameters from the
     * Floodlight module context.
     * @param config
     * @throws SyncException 
     */
    public void init(SyncManager syncManager,
                     FloodlightModuleContext context) throws SyncException;

    /**
     * Get the {@link ClusterConfig} that represents the current cluster
     * @return the {@link ClusterConfig} object
     * @throws SyncException
     */
    public ClusterConfig getConfig() throws SyncException;
}
