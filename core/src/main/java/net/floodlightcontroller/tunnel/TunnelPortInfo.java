package net.floodlightcontroller.tunnel;

import java.util.Arrays;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.util.MACAddress;

@SuppressFBWarnings(value="EI_EXPOSE_REP")
public class TunnelPortInfo {
    long dpid;
    short portNum;  // port number of the OFPortType.TUNNEL port
    short loopbackPortNum; // port number of the OFPortType.TUNNEL_LOOPBACK port
    int ipv4Addr;   // ipv4 addr of the tunnel-endpoint
    int ipv4AddrMask;
    byte[] macAddr; // mac addr of the tunnel-endpoint
    String enabled; // could be '', 'disabled', or 'yes'
    boolean active; // true when IP addr is learnt and tunneling is not disabled

    public TunnelPortInfo() {
        macAddr = new byte[] {0,0,0,0,0,0};
        portNum = -1;
        loopbackPortNum = -1;
        enabled = "";
    }

    public long getDpid() {
        return dpid;
    }

    public void setDpid(long dpid) {
        this.dpid = dpid;
    }

    public short getPortNum() {
        return portNum;
    }

    public void setPortNum(short portNum) {
        this.portNum = portNum;
    }

    public short getLoopbackPortNum() {
        return loopbackPortNum;
    }

    public void setLoopbackPortNum(short loopbackPortNum) {
        this.loopbackPortNum = loopbackPortNum;
    }

    public int getIpv4Addr() {
        return ipv4Addr;
    }

    public void setIpv4Addr(int ipv4Addr) {
        this.ipv4Addr = ipv4Addr;
    }

    public int getIpv4AddrMask() {
        return ipv4AddrMask;
    }

    public void setIpv4AddrMask(int ipv4AddrMask) {
        this.ipv4AddrMask = ipv4AddrMask;
    }

    public byte[] getMacAddr() {
        return macAddr;
    }

    public void setMacAddr(byte[] macAddr) {
        this.macAddr = macAddr;
    }

    public String getEnabled() {
        return enabled;
    }

    public void setEnabled(String enabled) {
        this.enabled = enabled;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append("{ ");
        strBuilder.append("dpid: ");
        strBuilder.append(dpid);
        strBuilder.append(" portnum: ");
        strBuilder.append(portNum);
        strBuilder.append(" loopbackPortNum: ");
        strBuilder.append(loopbackPortNum);
        strBuilder.append(" ipv4Addr: ");
        strBuilder.append(IPv4.fromIPv4Address(ipv4Addr));
        strBuilder.append(" ipv4AddrMask: ");
        strBuilder.append(IPv4.fromIPv4Address(ipv4AddrMask));
        strBuilder.append(" macAddr: ");
        strBuilder.append(MACAddress.valueOf(macAddr).toString());
        strBuilder.append(" enabled: ");
        strBuilder.append(enabled);
        strBuilder.append(" active: ");
        strBuilder.append(active);
        strBuilder.append("}");
        return strBuilder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (active ? 1231 : 1237);
        result = prime * result + (int) (dpid ^ (dpid >>> 32));
        result = prime * result
                 + ((enabled == null) ? 0 : enabled.hashCode());
        result = prime * result + ipv4Addr;
        result = prime * result + ipv4AddrMask;
        result = prime * result + loopbackPortNum;
        result = prime * result + Arrays.hashCode(macAddr);
        result = prime * result + portNum;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        TunnelPortInfo other = (TunnelPortInfo) obj;
        if (active != other.active) return false;
        if (dpid != other.dpid) return false;
        if (enabled == null) {
            if (other.enabled != null) return false;
        } else if (!enabled.equals(other.enabled)) return false;
        if (ipv4Addr != other.ipv4Addr) return false;
        if (ipv4AddrMask != other.ipv4AddrMask) return false;
        if (loopbackPortNum != other.loopbackPortNum) return false;
        if (!Arrays.equals(macAddr, other.macAddr)) return false;
        if (portNum != other.portNum) return false;
        return true;
    }
}
