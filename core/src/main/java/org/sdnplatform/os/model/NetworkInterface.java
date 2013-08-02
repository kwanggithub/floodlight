package org.sdnplatform.os.model;

import org.sdnplatform.os.IOSConfiglet.ConfigType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Network interface configuration
 * @author readams
 */
@SuppressFBWarnings(value="EI_EXPOSE_REP")
public class NetworkInterface implements OSModel {
    public enum ConfigMode {
        DHCP,
        STATIC;
        
        @JsonCreator
        public static ConfigMode forValue(String v) { 
            return ConfigMode.valueOf(v.toUpperCase());
        }
    }

    String type;
    int number;
    ConfigMode mode;

    String ipAddress;
    String netmask;
    
    FirewallRule[] firewallRules;

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public int getNumber() {
        return number;
    }
    public void setNumber(int number) {
        this.number = number;
    }
    @JsonProperty("config-mode")
    public ConfigMode getMode() {
        return mode;
    }
    @JsonProperty("config-mode")
    public void setMode(ConfigMode mode) {
        this.mode = mode;
    }
    @JsonProperty("ip-address")
    public String getIpAddress() {
        return ipAddress;
    }
    @JsonProperty("ip-address")
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    public String getNetmask() {
        return netmask;
    }
    public void setNetmask(String netmask) {
        this.netmask = netmask;
    }

    @ConfigApplyType({ConfigType.NETWORK_FIREWALL})
    @JsonProperty("firewall-rules")
    public FirewallRule[] getFirewallRules() {
        if (firewallRules == null) return new FirewallRule[] {};
        return firewallRules;
    }
    @ConfigApplyType({ConfigType.NETWORK_FIREWALL})
    @JsonProperty("firewall-rules")
    public void setFirewallRules(FirewallRule[] firewallRules) {
        this.firewallRules = firewallRules;
    }
}
