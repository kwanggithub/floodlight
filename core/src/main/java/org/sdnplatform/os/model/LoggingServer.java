package org.sdnplatform.os.model;

/**
 * A remote syslog server
 * @author readams
 */
public class LoggingServer implements OSModel {
    private String server;
    private String logLevel;

    public String getServer() {
        return server;
    }
    public void setServer(String server) {
        this.server = server;
    }
    public String getLogLevel() {
        if (logLevel == null) return "info";
        return logLevel;
    }
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
}
