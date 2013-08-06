package org.projectfloodlight.os.model;

import org.projectfloodlight.os.IOSConfiglet.ConfigType;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Object representing global cluster-wide OS configuration
 * @author readams
 */
public class GlobalConfig implements OSModel {
    private String loginBanner;
    private SNMPConfig snmpConfig;

    @ConfigApplyType({ConfigType.LOGIN_BANNER})
    @JsonProperty("login-banner")
    public String getLoginBanner() {
        return loginBanner;
    }

    @JsonProperty("login-banner")
    public void setLoginBanner(String loginBanner) {
        this.loginBanner = loginBanner;
    }

    @ConfigApplyType({ConfigType.SNMP})
    @JsonProperty("snmp-config")
    public SNMPConfig getSnmpConfig() {
        return snmpConfig;
    }

    @JsonProperty("snmp-config")
    public void setSnmpConfig(SNMPConfig snmpConfig) {
        this.snmpConfig = snmpConfig;
    }
}
