package com.bigswitch.floodlight.vendor;

import org.jboss.netty.buffer.ChannelBuffer;

public class OFMirrorSetVendorData extends OFBigSwitchVendorData {
    
    /**
     * Opcode/dataType to set mirroring
     */
    public static final int BSN_SET_MIRRORING = 3;

    protected byte reportMirrorPorts;
    protected byte pad1;
    protected byte pad2;
    protected byte pad3;
    
    public OFMirrorSetVendorData() {
        super(BSN_SET_MIRRORING);
        this.reportMirrorPorts=1;
    }

    public byte getReportMirrorPorts() {
        return reportMirrorPorts;
    }

    public void setReportMirrorPorts(byte report) {
        this.reportMirrorPorts = report;
    }
    
    /**
     * @return the total length vendor date
     */
    @Override
    public int getLength() {
        return super.getLength() + 4; // 4 extra bytes
    }
    
    /**
     * Read the vendor data from the channel buffer
     * @param data: the channel buffer from which we are deserializing
     * @param length: the length to the end of the enclosing message
     */
    public void readFrom(ChannelBuffer data, int length) {
        super.readFrom(data, length);
        reportMirrorPorts = data.readByte();
        pad1 = data.readByte();
        pad2 = data.readByte();
        pad3 = data.readByte();
    }
    
    /**
     * Write the vendor data to the channel buffer
     */
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeByte(reportMirrorPorts);
        data.writeByte(pad1);
        data.writeByte(pad2);
        data.writeByte(pad3);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + pad1;
        result = prime * result + pad2;
        result = prime * result + pad3;
        result = prime * result + reportMirrorPorts;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        OFMirrorSetVendorData other = (OFMirrorSetVendorData) obj;
        if (pad1 != other.pad1) return false;
        if (pad2 != other.pad2) return false;
        if (pad3 != other.pad3) return false;
        if (reportMirrorPorts != other.reportMirrorPorts) return false;
        return true;
    }
    
}
