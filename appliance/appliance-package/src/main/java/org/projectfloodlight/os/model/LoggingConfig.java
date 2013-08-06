package org.projectfloodlight.os.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Represent logging configuration on a controller node
 * @author readams
 */
@SuppressFBWarnings(value="EI_EXPOSE_REP")
public class LoggingConfig implements OSModel {
    private boolean loggingEnabled;
    private LoggingServer[] loggingServers;

    @JsonProperty("logging-enabled")
    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }
    @JsonProperty("logging-enabled")
    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }
    @JsonProperty("logging-servers")
    public LoggingServer[] getLoggingServers() {
        if (loggingServers == null) return new LoggingServer[] {};
        return loggingServers;
    }
    @JsonProperty("logging-servers")
    public void setLoggingServers(LoggingServer[] loggingServers) {
        this.loggingServers = loggingServers;
    }
}
