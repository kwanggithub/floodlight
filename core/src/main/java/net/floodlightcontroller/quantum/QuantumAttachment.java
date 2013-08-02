package net.floodlightcontroller.quantum;

import net.floodlightcontroller.util.MACAddress;

public class QuantumAttachment {
    private String id = null; // required
    private MACAddress mac = null; // required
    private String state = null; // optional

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }
    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }
    /**
     * @return the mac
     */
    public MACAddress getMac() {
        return mac;
    }
    /**
     * @param mac the mac to set
     */
    public void setMac(MACAddress mac) {
        this.mac = mac;
    }
    /**
     * @return the state
     */
    public String getState() {
        return state;
    }
    /**
     * @param state the state to set
     */
    public void setState(String state) {
        this.state = state;
    }
}
