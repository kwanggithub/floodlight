package net.floodlightcontroller.tunnel;

import org.openflow.util.HexString;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * SwitchTunnelInfo: A public class used to return tunnel information for
 * a switch (in getAllTunnels and getTunnelsOnSwitch methods). It includes
 * the switch dpid; a hexstring version of the dpid; the local-tunnel IP
 * addresses, the interface name for the tunnel-endpoint port, and tunnel
 * state (capable, enabled/disabled or active).
 */
public class SwitchTunnelInfo {
    public enum TunnelState {
        ENABLED, DISABLED, ACTIVE;

        @Override
        public String toString() {
            return this.name().toLowerCase();
        }

    }

    public static class Builder {
        // FIXME: make members private and add getters and setters
        // but since there's no invariant it's not so bad
        public long dpid;
        public String tunnelIPAddr; // FIXME  - show mask as well
        public String tunnelEndPointIntfName;
        public boolean tunnelCapable;
        public boolean tunnelEnabled;
        public boolean tunnelActive;

        private Builder() {
        }

        public SwitchTunnelInfo build() {
            return new SwitchTunnelInfo(this.dpid,
                                        this.tunnelIPAddr,
                                        this.tunnelEndPointIntfName,
                                        this.tunnelCapable,
                                        this.tunnelEnabled,
                                        this.tunnelActive);
        }
    }

    private final long dpid;
    private final String hexDpid;
    private final String tunnelIPAddr; // FIXME  - show mask as well
    private final String tunnelEndPointIntfName;
    final boolean tunnelCapable;
    // FIXME: tunnelEnalbed, tunnelActive and tunnelState are redundant
    final boolean tunnelEnabled;
    final boolean tunnelActive;
    private final SwitchTunnelInfo.TunnelState tunnelState;

    public SwitchTunnelInfo(long dpid,
                            String tunnelIPAddr,
                            String tunnelEndPointIntfName,
                            boolean tunnelCapable,
                            boolean tunnelEnabled,
                            boolean tunnelActive) {
        this.dpid = dpid;
        this.hexDpid = HexString.toHexString(dpid);
        this.tunnelIPAddr = tunnelIPAddr;
        this.tunnelEndPointIntfName = tunnelEndPointIntfName;
        this.tunnelCapable = tunnelCapable;
        this.tunnelEnabled = tunnelEnabled;
        this.tunnelActive = tunnelActive;
        if (this.tunnelActive)
            this.tunnelState = TunnelState.ACTIVE;
        else if (this.tunnelEnabled)
            tunnelState = TunnelState.ENABLED;
        else
            this.tunnelState = TunnelState.DISABLED;
    }
    @JsonIgnore
    public static SwitchTunnelInfo.Builder newBuilder() {
        return new Builder();
    }
    @JsonIgnore
    public long getDpid() {
        return dpid;
    }
    @JsonIgnore
    public String getHexDpid() {
        return hexDpid;
    }
    public String getTunnelIPAddr() {
        return tunnelIPAddr;
    }
    public String getTunnelEndPointIntfName() {
        return tunnelEndPointIntfName;
    }
    public boolean isTunnelCapable() {
        return tunnelCapable;
    }
    public boolean isTunnelEnabled() {
        return tunnelEnabled;
    }
    public boolean isTunnelActive() {
        return tunnelActive;
    }
    public SwitchTunnelInfo.TunnelState getTunnelState() {
        return tunnelState;
    }
}