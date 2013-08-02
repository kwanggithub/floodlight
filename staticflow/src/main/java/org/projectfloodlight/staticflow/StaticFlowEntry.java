package org.projectfloodlight.staticflow;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.openflow.protocol.OFFlowMod;

/**
 * Represents a Static Flow Entry
 * @author alexreimers
 *
 */
@JsonSerialize(using=StaticFlowEntrySerializer.class)
public class StaticFlowEntry {
    private boolean active;
    private OFFlowMod flowMod;
    private String name;
    private String dpid;

    public StaticFlowEntry(String name, String dpid, OFFlowMod flowMod, boolean active) {
        this.name = name;
        this.dpid = dpid;
        this.flowMod = flowMod;
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OFFlowMod getFlowMod() {
        return flowMod;
    }

    public void setFlowMod(OFFlowMod flowMod) {
        this.flowMod = flowMod;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDpid() {
        return dpid;
    }

    public void setDpid(String dpid) {
        this.dpid = dpid;
    }
}
