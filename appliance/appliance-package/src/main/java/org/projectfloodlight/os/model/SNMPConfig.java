package org.projectfloodlight.os.model;

/**
 * Represent configuration for the SNMP client
 * @author readams
 */
public class SNMPConfig implements OSModel {
    private boolean enabled;
    private String community;
    private String location;
    private String contact;
    
    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    public String getCommunity() {
        return community;
    }
    public void setCommunity(String community) {
        this.community = community;
    }
    public String getLocation() {
        return location;
    }
    public void setLocation(String location) {
        this.location = location;
    }
    public String getContact() {
        return contact;
    }
    public void setContact(String contact) {
        this.contact = contact;
    }    
}
