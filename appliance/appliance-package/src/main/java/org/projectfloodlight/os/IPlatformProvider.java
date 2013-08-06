package org.projectfloodlight.os;

import java.io.File;
import java.util.List;

import org.projectfloodlight.os.model.OSAction;
import org.projectfloodlight.os.model.OSConfig;

/**
 * Provides configuration for a given platform
 * @author readams
 */
public interface IPlatformProvider {
    /**
     * Check whether this provider should be applied to the current system 
     * @param the base path for the system
     * @return <code>true</code> if this provider should be activated
     */
    public boolean isApplicable(File basePath);
    
    /**
     * Apply the configuration in the includes {@link OSConfig} object
     * to the underlying system.  This method will be executed from within
     * the privileged subprocess
     * @param basePath the base path where the configuration will be applied
     * @param oldConfig the previous configuration state
     * @param newConfig the new configuration to apply
     * @return a {@link WrapperOutput} containing status information
     */
    public WrapperOutput applyConfiguration(File basePath,
                                            OSConfig oldConfig, 
                                            OSConfig newConfig);
    
    /**
     * Execute the given actions on the system
     * @param basePath the base path against which the actions will apply
     * @param action the action model
     * @return a {@link WrapperOutput} containing status information
     */
    public WrapperOutput applyAction(File basePath, OSAction action);
    
    /**
     * Construct argument array for executing privileged wrapper script
     * @param privedWrapper path to the wrapper script
     * @param basePath the base path to pass to the script
     * @param cachePath the cache path to pass to the script
     * @param runWithPrivs if false, just run as a subprocess without
     * trying to elevate privileges
     * @param dryRun whether to set the dry run option for the script
     * @param action <code>true</code> if this is an action.  
     * <code>false</code> if this is config
     * @return the {@link Process}
     */
    public List<String> getWrapperArgs(String privedWrapper,
                                       String basePath, 
                                       String cachePath,
                                       boolean runWithPrivs,
                                       boolean dryRun,
                                       boolean action);
}
