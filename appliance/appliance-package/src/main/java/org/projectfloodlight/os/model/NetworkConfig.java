package org.projectfloodlight.os.model;

import org.projectfloodlight.os.IOSConfiglet.ConfigType;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Represent network configuration on a controller node
 * @author readams
 */
@SuppressFBWarnings(value="EI_EXPOSE_REP")
public class NetworkConfig implements OSModel {
    private boolean domainLookupsEnabled;
    private String[] dnsServers;
    private String[] dnsSearchPath;
    private String defaultGateway;
    private NetworkInterface[] networkInterfaces;

    @ConfigApplyType({ConfigType.NETWORK_DNS})
    @JsonProperty("domain-lookups-enabled")
    public boolean isDomainLookupsEnabled() {
        return domainLookupsEnabled;
    }
    @JsonProperty("domain-lookups-enabled")
    public void setDomainLookupsEnabled(boolean domainLookupsEnabled) {
        this.domainLookupsEnabled = domainLookupsEnabled;
    }
    @ConfigApplyType({ConfigType.NETWORK_DNS})
    @JsonProperty("dns-servers")
    public String[] getDnsServers() {
        if (dnsServers == null) return new String[] {};
        return dnsServers;
    }
    @JsonProperty("dns-servers")
    public void setDnsServers(String[] dnsServers) {
        this.dnsServers = dnsServers;
    }
    @ConfigApplyType({ConfigType.NETWORK_DNS})
    @JsonProperty("dns-search-path")
    public String[] getDnsSearchPath() {
        return dnsSearchPath;
    }
    @JsonProperty("dns-search-path")
    public void setDnsSearchPath(String[] dnsSearchPath) {
        this.dnsSearchPath = dnsSearchPath;
    }
    @ConfigApplyType({ConfigType.NETWORK_INTERFACES})
    @JsonProperty("default-gateway")
    public String getDefaultGateway() {
        return defaultGateway;
    }
    @JsonProperty("default-gateway")
    public void setDefaultGateway(String defaultGateway) {
        this.defaultGateway = defaultGateway;
    }
    @ConfigApplyType({ConfigType.NETWORK_INTERFACES, ConfigType.NETWORK_FIREWALL})
    @JsonProperty("network-interfaces")
    public NetworkInterface[] getNetworkInterfaces() {
        if (networkInterfaces == null) return new NetworkInterface[] {};
        return networkInterfaces;
    }
    @JsonProperty("network-interfaces")
    public void setNetworkInterfaces(NetworkInterface[] networkInterfaces) {
        this.networkInterfaces = networkInterfaces;
    }
}
