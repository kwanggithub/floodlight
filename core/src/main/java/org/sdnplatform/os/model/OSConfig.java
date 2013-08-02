package org.sdnplatform.os.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Object representing both global and node-specific configuration for
 * a controller node
 * @author readams
 */
public class OSConfig implements OSModel {
    private ControllerNode nodeConfig;
    private GlobalConfig globalConfig;

    @JsonProperty("local-node")
    public ControllerNode getNodeConfig() {
        return nodeConfig;
    }

    @JsonProperty("local-node")
    public void setNodeConfig(ControllerNode nodeConfig) {
        this.nodeConfig = nodeConfig;
    }

    @JsonProperty("global")
    public GlobalConfig getGlobalConfig() {
        return globalConfig;
    }

    @JsonProperty("global")
    public void setGlobalConfig(GlobalConfig globalConfig) {
        this.globalConfig = globalConfig;
    }
}
