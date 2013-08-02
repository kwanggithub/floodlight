package com.bigswitch.floodlight.vendor;

import org.jboss.netty.buffer.ChannelBuffer;


/**
 * Class that represents the vendor data in the netmask table request
 * extension implemented by Arista switches
 * 
 * @author munish_mehta (munish.mehta@bigswitch.com)
 */

public class OFNetmaskVendorData extends OFBigSwitchVendorData {

    /**
     * Table index for set or get of the the entry from netmask table
     */
    protected byte tableIndex;
    protected byte pad1;
    protected byte pad2;
    protected byte pad3;
    protected int  netMask;
    
    public OFNetmaskVendorData(int dataType) {
        super(dataType);
        this.tableIndex = 0;
        this.netMask = (int)0xffffffffL;
    }

    public OFNetmaskVendorData(int dataType, byte table_index, int netmask) {
        super(dataType);
        this.tableIndex = table_index;
        this.netMask = netmask;
    }


    public byte getTableIndex() {
        return tableIndex;
    }

    public void setTableIndex(byte tableIndex) {
        this.tableIndex = tableIndex;
    }

    public int getNetMask() {
        return netMask;
    }

    public void setNetMask(int netMask) {
        this.netMask = netMask;
    }

    /**
     * @return the total length of the netmask vendor data
     */
    @Override
    public int getLength() {
        return super.getLength() + 8; // 8 extra bytes
    }
    
    /**
     * Read the vendor data from the channel buffer
     * @param data: the channel buffer from which we are deserializing
     * @param length: the length to the end of the enclosing message
     */
    public void readFrom(ChannelBuffer data, int length) {
        super.readFrom(data, length);
        tableIndex = data.readByte();
        pad1 = data.readByte();
        pad2 = data.readByte();
        pad3 = data.readByte();
        netMask = data.readInt();
    }
    
    /**
     * Write the vendor data to the channel buffer
     */
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeByte(tableIndex);
        data.writeByte(pad1);
        data.writeByte(pad2);
        data.writeByte(pad3);
        data.writeInt(netMask);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + netMask;
        result = prime * result + pad1;
        result = prime * result + pad2;
        result = prime * result + pad3;
        result = prime * result + tableIndex;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        OFNetmaskVendorData other = (OFNetmaskVendorData) obj;
        if (netMask != other.netMask) return false;
        if (pad1 != other.pad1) return false;
        if (pad2 != other.pad2) return false;
        if (pad3 != other.pad3) return false;
        if (tableIndex != other.tableIndex) return false;
        return true;
    }
    

}
