package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;

public class ICMPv6 extends BasePacket {
    protected byte icmpType;
    protected byte icmpCode;
    protected short checksum;

    /**
     * @return the icmpType
     */
    public byte getIcmpType() {
        return icmpType;
    }

    /**
     * @param icmpType to set
     */
    public ICMPv6 setIcmpType(byte icmpType) {
        this.icmpType = icmpType;
        return this;
    }

    /**
     * @return the icmp code
     */
    public byte getIcmpCode() {
        return icmpCode;
    }

    /**
     * @param icmpCode code to set
     */
    public ICMPv6 setIcmpCode(byte icmpCode) {
        this.icmpCode = icmpCode;
        return this;
    }

    /**
     * @return the checksum
     */
    public short getChecksum() {
        return checksum;
    }

    /**
     * @param checksum the checksum to set
     */
    public ICMPv6 setChecksum(short checksum) {
        this.checksum = checksum;
        return this;
    }

    /**
     * Serializes the packet. Will compute and set the following fields if they
     * are set to specific values at the time serialize is called:
     *      -checksum : 0
     *      -length : 0
     */
    public byte[] serialize() {
        int length = 4;
        byte[] payloadData = null;
        if (payload != null) {
            payload.setParent(this);
            payloadData = payload.serialize();
            length += payloadData.length;
        }

        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);

        bb.put(this.icmpType);
        bb.put(this.icmpCode);
        bb.putShort(this.checksum);
        if (payloadData != null)
            bb.put(payloadData);

        // compute checksum if needed
        if (this.checksum == 0) {
            bb.rewind();
            int accumulation = 0;

            for (int i = 0; i < length / 2; ++i) {
                accumulation += 0xffff & bb.getShort();
            }
            // pad to an even number of shorts
            if (length % 2 > 0) {
                accumulation += (bb.get() & 0xff) << 8;
            }

            accumulation = ((accumulation >> 16) & 0xffff)
                    + (accumulation & 0xffff);
            this.checksum = (short) (~accumulation & 0xffff);
            bb.putShort(2, this.checksum);
        }
        return data;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 5807;
        int result = super.hashCode();
        result = prime * result + icmpType;
        result = prime * result + icmpCode;
        result = prime * result + checksum;
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (!(obj instanceof ICMPv6))
            return false;
        ICMPv6 other = (ICMPv6) obj;
        if (icmpType != other.icmpType)
            return false;
        if (icmpCode != other.icmpCode)
            return false;
        if (checksum != other.checksum)
            return false;
        return true;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        this.icmpType = bb.get();
        this.icmpCode = bb.get();
        this.checksum = bb.getShort();
        
        this.payload = new Data();
        this.payload = payload.deserialize(data, bb.position(), bb.limit()-bb.position());
        this.payload.setParent(this);
        return this;
    }
}
