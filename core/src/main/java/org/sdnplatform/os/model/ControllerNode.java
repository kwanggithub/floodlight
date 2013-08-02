package org.sdnplatform.os.model;

import org.sdnplatform.os.IOSConfiglet.ConfigType;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represent the system configuration for a specific controller node
 * @author readams
 */
public class ControllerNode implements OSModel {
    private NetworkConfig networkConfig;
    private TimeConfig timeConfig;
    private LoggingConfig loggingConfig;

    @JsonProperty("network-config")
    public NetworkConfig getNetworkConfig() {
        return networkConfig;
    }
    @JsonProperty("network-config")
    public void setNetworkConfig(NetworkConfig networkConfig) {
        this.networkConfig = networkConfig;
    }
    @JsonProperty("time-config")
    public TimeConfig getTimeConfig() {
        return timeConfig;
    }
    @JsonProperty("time-config")
    public void setTimeConfig(TimeConfig timeConfig) {
        this.timeConfig = timeConfig;
    }
    @ConfigApplyType({ConfigType.LOGGING})
    @JsonProperty("logging-config")
    public LoggingConfig getLoggingConfig() {
        return loggingConfig;
    }
    @JsonProperty("logging-config")
    public void setLoggingConfig(LoggingConfig loggingConfig) {
        this.loggingConfig = loggingConfig;
    }
}
