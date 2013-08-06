package org.projectfloodlight.os.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FirewallRule implements OSModel {
    enum Action {
        ALLOW, 
        DENY, 
        REJECT
    }
    
    Action action;
    String srcIp;
    String vrrpIp;
    int port;
    String proto;

    public Action getAction() {
        return action;
    }
    public void setAction(Action action) {
        this.action = action;
    }
    @JsonProperty("src-ip")
    public String getSrcIp() {
        return srcIp;
    }
    @JsonProperty("src-ip")
    public void setSrcIp(String srcIp) {
        this.srcIp = srcIp;
    }
    @JsonProperty("vrrp-ip")
    public String getVrrpIp() {
        return vrrpIp;
    }
    @JsonProperty("vrrp-ip")
    public void setVrrpIp(String vrrpIp) {
        this.vrrpIp = vrrpIp;
    }
    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }
    public String getProto() {
        return proto;
    }
    public void setProto(String proto) {
        this.proto = proto;
    }
}
