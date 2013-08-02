package org.sdnplatform.os;

import java.io.File;

import org.sdnplatform.os.model.OSConfig;

/**
 * An interface for an OS configlet, which is a way to configure some specific
 * underlying OS functionality.
 * @author readams
 */
public interface IOSConfiglet extends IOSlet<IOSConfiglet.ConfigType> {
    public enum ConfigType {
        NETWORK_INTERFACES,
        NETWORK_FIREWALL,
        NETWORK_DNS,
        TIME_ZONE,
        TIME_NTP,
        LOGGING,
        LOGIN_BANNER,
        SNMP,
    }
        
    /**
     * Apply the configuration assocated with this configlet to the system
     * @param oldConfig the old configuration.  May be null
     * @param newConfig the new configuration
     * @return a {@link WrapperOutput} containing the results of the operation
     */
    public WrapperOutput applyConfig(File basePath,
                                     OSConfig oldConfig,
                                     OSConfig newConfig);
}
